package main

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"os/signal"
	"reflect"
	"runtime"
	"syscall"
	"time"

	"virtual-barcode-bridge/keyboard"
	"virtual-barcode-bridge/network"
	"virtual-barcode-bridge/server"
)

type usbController interface {
	SetActive(bool)
	Select(string)
	Close()
}

var usb usbController

func main() {
	port := flag.Int("port", 8080, "listen port")
	ipFlag := flag.String("ip", "", "LAN IP shown in QR (default: auto-detect)")
	noQR := flag.Bool("no-qr", false, "skip terminal QR rendering")
	noBrowser := flag.Bool("no-browser", false, "don't auto-open the browser")
	noNative := flag.Bool("no-native", false, "don't open the native status window (Windows)")
	flag.Parse()
	secret, err := newPairingSecret()
	if err != nil {
		fatalDesktop(fmt.Sprintf("Cannot create pairing code: %v", err))
	}

	options, discoveryErr := network.Discover(*port, *ipFlag)
	if discoveryErr != nil {
		log.Printf("network discovery failed: %v", discoveryErr)
	}
	options = pairedOptions(options, secret)
	wsURL := withPairing(fmt.Sprintf("ws://127.0.0.1:%d/ws", *port), secret)
	if len(options) > 0 {
		wsURL = options[0].URL
	}
	for _, option := range options {
		fmt.Printf("Network: %s (%s)\n", option.Label, option.URL)
	}
	uiURL := fmt.Sprintf("http://localhost:%d", *port)

	fmt.Printf("ScanBridge\n")
	fmt.Printf("WebSocket endpoint: %s\n", wsURL)
	fmt.Printf("Web UI:            %s\n", uiURL)
	if !*noQR && len(options) > 0 {
		fmt.Println("\nScan with your phone:")
		if err := server.PrintQR(wsURL); err != nil {
			log.Printf("QR render failed: %v", err)
		}
	}

	srv := server.New(keyboard.New(), wsURL, options...)
	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		fatalDesktop(fmt.Sprintf("Port %d tidak tersedia: %v", *port, err))
	}
	usb = newUSBManager(*port)
	defer usb.Close()

	mux := http.NewServeMux()
	mux.Handle("/", srv.Handler())
	mux.HandleFunc("/usb/status", handleUSBStatus)
	mux.HandleFunc("/usb/control", handleUSBControl)
	httpSrv := &http.Server{Handler: mux}
	go func() {
		if err := httpSrv.Serve(ln); err != nil && err != http.ErrServerClosed {
			log.Fatalf("server: %v", err)
		}
	}()
	go refreshNetworks(srv, *port, *ipFlag, secret, options)

	usingNative := false
	if !*noNative {
		if openNativeWindow(wsURL, options...) {
			usingNative = true
			fmt.Println("Native status window opened; shutting it down stops the bridge.")
		} else if runtime.GOOS == "windows" {
			log.Printf("native window unavailable, falling back to web UI")
		}
	}

	if !*noBrowser && !usingNative {
		go openBrowser(uiURL)
	}

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop
	fmt.Println("\nShutting down")
}

func localRequest(r *http.Request) bool {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil || net.ParseIP(host) == nil || !net.ParseIP(host).IsLoopback() {
		return false
	}
	requestHost := r.Host
	if name, _, err := net.SplitHostPort(requestHost); err == nil {
		requestHost = name
	}
	return requestHost == "localhost" || (net.ParseIP(requestHost) != nil && net.ParseIP(requestHost).IsLoopback())
}

func newPairingSecret() (string, error) {
	data := make([]byte, 24)
	if _, err := rand.Read(data); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(data), nil
}

func withPairing(endpoint, secret string) string {
	parsed, err := url.Parse(endpoint)
	if err != nil {
		return endpoint
	}
	query := parsed.Query()
	query.Set("pair", secret)
	parsed.RawQuery = query.Encode()
	return parsed.String()
}

func pairedOptions(options []network.Option, secret string) []network.Option {
	paired := append([]network.Option(nil), options...)
	for i := range paired {
		paired[i].URL = withPairing(paired[i].URL, secret)
	}
	return paired
}

// USB control is limited to local desktop browsers, not clients on the LAN.
func handleUSBStatus(w http.ResponseWriter, r *http.Request) {
	if !localRequest(r) {
		http.Error(w, "local access only", http.StatusForbidden)
		return
	}
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	if controller, ok := usb.(interface{ WebStatus() any }); ok {
		_ = json.NewEncoder(w).Encode(controller.WebStatus())
	} else {
		_ = json.NewEncoder(w).Encode(map[string]any{"available": false})
	}
}

func handleUSBControl(w http.ResponseWriter, r *http.Request) {
	if !localRequest(r) {
		http.Error(w, "local access only", http.StatusForbidden)
		return
	}
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	// A local service can be reached by a malicious web page; require same-origin
	// browser requests before changing ADB mappings.
	if origin := r.Header.Get("Origin"); origin == "" || origin != "http://"+r.Host {
		http.Error(w, "same origin required", http.StatusForbidden)
		return
	}
	var request struct {
		Active   *bool  `json:"active"`
		Selected string `json:"selected"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1024)).Decode(&request); err != nil {
		http.Error(w, "invalid request", http.StatusBadRequest)
		return
	}
	if request.Active != nil {
		usb.SetActive(*request.Active)
	}
	if request.Selected != "" {
		// Only a serial reported by ADB can be selected.
		controller, ok := usb.(interface{ SelectValid(string) bool })
		if !ok || !controller.SelectValid(request.Selected) {
			http.Error(w, "unknown device", http.StatusBadRequest)
			return
		}
	}
	w.WriteHeader(http.StatusNoContent)
}

// refreshNetworks makes adapters added after startup (for example Android USB
// tethering) selectable from the web UI without adding a protocol or listener.
func refreshNetworks(srv *server.Server, port int, override, secret string, current []network.Option) {
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()
	for range ticker.C {
		options, err := network.Discover(port, override)
		if err != nil {
			log.Printf("network discovery refresh failed: %v", err)
			continue
		}
		options = pairedOptions(options, secret)
		if reflect.DeepEqual(current, options) {
			continue
		}
		current = options
		srv.UpdateNetworks(options)
		updateNativeNetworks(options)
		log.Printf("network interfaces updated:")
		for _, option := range options {
			log.Printf("  %s (%s)", option.Label, option.URL)
		}
	}
}

// openBrowser opens url in the default browser without blocking.
func openBrowser(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("cmd", "/c", "start", "", url)
	case "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	if err := cmd.Start(); err != nil {
		log.Printf("open browser: %v (keep using the printed URL)", err)
		return
	}
	go cmd.Wait()
}

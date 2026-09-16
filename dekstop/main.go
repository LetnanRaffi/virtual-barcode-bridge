package main

import (
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"runtime"
	"syscall"

	"virtual-barcode-bridge/keyboard"
	"virtual-barcode-bridge/network"
	"virtual-barcode-bridge/server"
)

func main() {
	port := flag.Int("port", 8080, "listen port")
	ipFlag := flag.String("ip", "", "LAN IP shown in QR (default: auto-detect)")
	noQR := flag.Bool("no-qr", false, "skip terminal QR rendering")
	noBrowser := flag.Bool("no-browser", false, "don't auto-open the browser")
	noNative := flag.Bool("no-native", false, "don't open the native status window (Windows)")
	flag.Parse()

	ip := *ipFlag
	if ip == "" {
		ips := network.AllNonLoopbackIPs()
		if len(ips) == 0 {
			var err error
			ip, err = network.LocalIP()
			if err != nil {
				log.Fatalf("detect LAN IP: %v", err)
			}
		} else {
			ip = ips[0]
			if len(ips) > 1 {
				fmt.Printf("Multiple LAN IPs detected; using %s. Override with -ip.\n", ip)
				for _, a := range ips {
					fmt.Printf("  - %s\n", a)
				}
			}
		}
	}

	wsURL := fmt.Sprintf("ws://%s:%d/ws", ip, *port)
	uiURL := fmt.Sprintf("http://localhost:%d", *port)

	fmt.Printf("Virtual Barcode Bridge\n")
	fmt.Printf("WebSocket endpoint: %s\n", wsURL)
	fmt.Printf("Web UI:            %s\n", uiURL)
	if !*noQR {
		fmt.Println("\nScan with your phone:")
		if err := server.PrintQR(wsURL); err != nil {
			log.Printf("QR render failed: %v", err)
		}
	}

	srv := server.New(keyboard.New(), wsURL)

	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		log.Fatalf("listen port %d: %v", *port, err)
	}

	httpSrv := &http.Server{Handler: srv.Handler()}
	go func() {
		if err := httpSrv.Serve(ln); err != nil && err != http.ErrServerClosed {
			log.Fatalf("server: %v", err)
		}
	}()

	usingNative := false
	if !*noNative {
		if openNativeWindow(wsURL) {
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

// Package server hosts the WebSocket endpoint, web UI, and QR rendering.
package server

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/skip2/go-qrcode"

	"virtual-barcode-bridge/keyboard"
)

//go:embed web/index.html
var indexHTML []byte

// Payload is the JSON message sent by the mobile scanner.
type Payload struct {
	Type      string `json:"type"`
	Data      string `json:"data"`
	AutoEnter *bool  `json:"auto_enter"`
}

// monitorEvent is pushed to web UI clients over /monitor.
type monitorEvent struct {
	Time string `json:"time"`
	Kind string `json:"kind"`
	Msg  string `json:"msg"`
}

type monitorHub struct {
	mu      sync.Mutex
	clients map[*websocket.Conn]bool
	history []monitorEvent
}

const (
	historyCap = 200
	writeWait  = 3 * time.Second
)

func newMonitorHub() *monitorHub {
	return &monitorHub{clients: make(map[*websocket.Conn]bool)}
}

// emit prints to the terminal and pushes to every web UI client.
func (h *monitorHub) emit(kind, format string, args ...any) {
	ev := monitorEvent{
		Time: time.Now().Format("15:04:05"),
		Kind: kind,
		Msg:  fmt.Sprintf(format, args...),
	}
	terminalLog.Printf("%s [%s] %s", ev.Time, ev.Kind, ev.Msg)

	h.mu.Lock()
	h.history = append(h.history, ev)
	if len(h.history) > historyCap {
		h.history = h.history[len(h.history)-historyCap:]
	}
	for c := range h.clients {
		c.SetWriteDeadline(time.Now().Add(writeWait))
		if err := c.WriteJSON(ev); err != nil {
			c.Close()
			delete(h.clients, c)
		}
	}
	h.mu.Unlock()
}

type Server struct {
	kb      keyboard.Injector
	mu      sync.Mutex
	monitor *monitorHub
	qrPNG   []byte
	wsURL   string
}

func New(kb keyboard.Injector, wsURL string) *Server {
	s := &Server{kb: kb, monitor: newMonitorHub(), wsURL: wsURL}
	if q, err := qrcode.New(wsURL, qrcode.Medium); err == nil {
		s.qrPNG, _ = q.PNG(384)
	}
	return s
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/", s.handleIndex)
	mux.HandleFunc("/qr.png", s.handleQR)
	mux.HandleFunc("/ws", s.handleWS)
	mux.HandleFunc("/monitor", s.handleMonitor)
	mux.HandleFunc("/inject", s.handleInject)
	return mux
}

var upgrader = websocket.Upgrader{
	CheckOrigin: func(*http.Request) bool { return true },
}

func (s *Server) handleIndex(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	page := strings.ReplaceAll(string(indexHTML), "__WS_URL__", s.wsURL)
	w.Write([]byte(page))
}

func (s *Server) handleQR(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "image/png")
	w.Write(s.qrPNG)
}

func (s *Server) handleWS(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		s.monitor.emit("error", "upgrade failed from %s: %v", r.RemoteAddr, err)
		return
	}
	defer conn.Close()

	a := conn.RemoteAddr().String()
	s.monitor.emit("conn", "Client connected: %s", a)

	for {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			break
		}
		var p Payload
		if err := json.Unmarshal(msg, &p); err != nil {
			s.monitor.emit("warn", "bad payload from %s: %v", a, err)
			continue
		}
		if p.Type != "scan" || p.Data == "" {
			continue
		}
		autoEnter := true
		if p.AutoEnter != nil {
			autoEnter = *p.AutoEnter
		}
		s.inject(p.Data, autoEnter, a)
	}

	s.monitor.emit("disc", "Client disconnected: %s", a)
}

func (s *Server) handleInject(w http.ResponseWriter, r *http.Request) {
	var p Payload
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil {
		http.Error(w, "bad json", http.StatusBadRequest)
		return
	}
	if p.Type != "" && p.Type != "scan" {
		http.Error(w, "bad type", http.StatusBadRequest)
		return
	}
	if p.Data == "" {
		http.Error(w, "empty data", http.StatusBadRequest)
		return
	}
	autoEnter := true
	if p.AutoEnter != nil {
		autoEnter = *p.AutoEnter
	}
	if err := s.inject(p.Data, autoEnter, "desktop"); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Write([]byte(`{"ok":true}`))
}

func (s *Server) handleMonitor(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	defer conn.Close()

	h := s.monitor
	h.mu.Lock()
	h.clients[conn] = true
	for _, ev := range h.history {
		conn.SetWriteDeadline(time.Now().Add(writeWait))
		if err := conn.WriteJSON(ev); err != nil {
			h.mu.Unlock()
			return
		}
	}
	h.mu.Unlock()

	for {
		if _, _, err := conn.ReadMessage(); err != nil {
			break
		}
	}
	h.mu.Lock()
	delete(h.clients, conn)
	h.mu.Unlock()
}

// inject types keystrokes and reports the outcome to terminal + web UI.
func (s *Server) inject(data string, autoEnter bool, source string) error {
	s.mu.Lock()
	typed := s.kb.Type(data)
	entered := error(nil)
	if autoEnter {
		entered = s.kb.Enter()
	}
	s.mu.Unlock()

	if typed != nil {
		s.monitor.emit("error", "type failed: %v", typed)
		return typed
	}
	if entered != nil {
		s.monitor.emit("error", "enter failed: %v", entered)
		return entered
	}
	s.monitor.emit("scan", "Scan %q from %s (enter=%v)", data, source, autoEnter)
	return nil
}

var terminalLog = log.Default()

// PrintQR renders the pairing QR code in the terminal.
func PrintQR(url string) error {
	if len(url) == 0 {
		return fmt.Errorf("empty websocket URL")
	}
	q, err := qrcode.New(url, qrcode.Medium)
	if err != nil {
		return err
	}
	for _, line := range q.Bitmap() {
		for _, dark := range line {
			if dark {
				fmt.Print("██")
			} else {
				fmt.Print("  ")
			}
		}
		fmt.Println()
	}
	return nil
}
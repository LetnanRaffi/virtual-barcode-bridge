package server

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gorilla/websocket"
)

func TestPairingRequiredForLANWebSocket(t *testing.T) {
	kb := &countingKeyboard{}
	s := New(kb, "ws://192.168.1.2:8080/ws?pair=secret")
	handler := s.Handler()
	host := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.RemoteAddr = "192.168.1.9:12345"
		handler.ServeHTTP(w, r)
	}))
	defer host.Close()
	wsURL := "ws" + strings.TrimPrefix(host.URL, "http") + "/ws"
	if conn, response, err := websocket.DefaultDialer.Dial(wsURL, nil); err == nil {
		conn.Close()
		t.Fatal("LAN websocket accepted without pairing")
	} else if response == nil || response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("missing pairing status: %v %v", response, err)
	}
	if conn, response, err := websocket.DefaultDialer.Dial(wsURL+"?pair=wrong", nil); err == nil {
		conn.Close()
		t.Fatal("LAN websocket accepted wrong pairing")
	} else if response == nil || response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("wrong pairing status: %v %v", response, err)
	}
	conn, _, err := websocket.DefaultDialer.Dial(wsURL+"?pair=secret", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	var hello map[string]any
	if err := conn.ReadJSON(&hello); err != nil || hello["type"] != "hello" {
		t.Fatalf("valid pairing hello: %v %+v", err, hello)
	}
	if err := conn.WriteJSON(Payload{Type: "barcode", ID: "paired-1", Value: "ABC123"}); err != nil {
		t.Fatal(err)
	}
	var ack barcodeAck
	if err := conn.ReadJSON(&ack); err != nil || !ack.Success || len(kb.values) != 1 {
		t.Fatalf("paired scan: %v %+v values=%v", err, ack, kb.values)
	}
}

func TestMissingServerPairingCodeStillBlocksLAN(t *testing.T) {
	handler := New(&countingKeyboard{}, "ws://192.168.1.2:8080/ws").Handler()
	r := httptest.NewRequest(http.MethodGet, "http://192.168.1.2:8080/ws", nil)
	r.RemoteAddr = "192.168.1.9:12345"
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("unpaired server LAN status: %d", w.Code)
	}
}

func TestDesktopControlsAreLocalAndSameOrigin(t *testing.T) {
	s := New(&countingKeyboard{}, "ws://192.168.1.2:8080/ws?pair=secret")
	handler := s.Handler()
	for _, path := range []string{"/", "/networks", "/qr.png", "/monitor", "/inject"} {
		r := httptest.NewRequest(http.MethodGet, path, nil)
		r.RemoteAddr = "192.168.1.9:12345"
		w := httptest.NewRecorder()
		handler.ServeHTTP(w, r)
		if w.Code != http.StatusForbidden {
			t.Errorf("LAN access to %s: %d", path, w.Code)
		}
	}
	r := httptest.NewRequest(http.MethodPost, "http://localhost:8080/inject", strings.NewReader(`{"data":"ABC"}`))
	r.Header.Set("Origin", "http://evil.example")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	if w.Code != http.StatusForbidden {
		t.Fatalf("cross-origin injection status: %d", w.Code)
	}
	r = httptest.NewRequest(http.MethodGet, "http://evil.example/networks", nil)
	w = httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	if w.Code != http.StatusForbidden {
		t.Fatalf("non-local Host header status: %d", w.Code)
	}
}

func TestInvalidBarcodeValueIsRejected(t *testing.T) {
	for _, value := range []string{"", "  ", "A\nB", strings.Repeat("A", 257)} {
		if validBarcodeValue(value) {
			t.Errorf("accepted invalid value %q", value)
		}
	}
	if !validBarcodeValue("EAN 4006381333931") {
		t.Fatal("ordinary barcode rejected")
	}
}

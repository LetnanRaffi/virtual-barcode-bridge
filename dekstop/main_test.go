package main

import (
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"strings"
	"testing"

	"virtual-barcode-bridge/network"
	"virtual-barcode-bridge/server"
)

func TestPairingURLsUseOneSecret(t *testing.T) {
	secret, err := newPairingSecret()
	if err != nil || len(secret) < 24 {
		t.Fatalf("pairing secret: %q %v", secret, err)
	}
	options := pairedOptions([]network.Option{{Label: "Wi-Fi", URL: "ws://192.168.1.2:8080/ws"}}, secret)
	if len(options) != 1 || !strings.HasSuffix(options[0].URL, "?pair="+secret) {
		t.Fatalf("paired options: %+v", options)
	}
}

func TestUSBControlLocalHostCheck(t *testing.T) {
	r := httptest.NewRequest("GET", "http://localhost:8080/usb/status", nil)
	r.RemoteAddr = "127.0.0.1:12345"
	if !localRequest(r) {
		t.Fatal("localhost request rejected")
	}
	r.Host = "evil.example"
	if localRequest(r) {
		t.Fatal("DNS rebinding Host accepted")
	}
	r.Host = "localhost:8080"
	r.RemoteAddr = "192.168.1.9:12345"
	if localRequest(r) {
		t.Fatal("LAN request accepted")
	}
}

func TestPayloadUnmarshal(t *testing.T) {
	data := []byte(`{"type":"scan","data":"ABC123","auto_enter":false}`)
	var p server.Payload
	if err := json.Unmarshal(data, &p); err != nil {
		t.Fatal(err)
	}
	if p.Data != "ABC123" {
		t.Errorf("expected ABC123, got %s", p.Data)
	}
	if p.AutoEnter == nil || *p.AutoEnter != false {
		t.Error("auto_enter should be false")
	}
}

func TestPayloadAutoEnterDefault(t *testing.T) {
	data := []byte(`{"type":"scan","data":"X"}`)
	var p server.Payload
	if err := json.Unmarshal(data, &p); err != nil {
		t.Fatal(err)
	}
	if p.AutoEnter != nil {
		t.Error("auto_enter should be nil (default true)")
	}
}

func TestLocalIP(t *testing.T) {
	ip, err := network.LocalIP()
	if err != nil {
		t.Fatal(err)
	}
	if ip == "" {
		t.Error("empty local IP")
	}
	t.Logf("LocalIP: %s", ip)
}

func TestQR(t *testing.T) {
	ip := "127.0.0.1"
	port := 8080
	url := fmt.Sprintf("ws://%s:%d/ws", ip, port)
	if err := server.PrintQR(url); err != nil {
		t.Fatal(err)
	}
}

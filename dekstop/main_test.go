package main

import (
	"encoding/json"
	"fmt"
	"testing"

	"virtual-barcode-bridge/server"
	"virtual-barcode-bridge/network"
)

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
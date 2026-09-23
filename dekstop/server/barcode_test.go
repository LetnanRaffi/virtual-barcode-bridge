package server

import (
	"errors"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gorilla/websocket"
)

type countingKeyboard struct {
	values []string
	fail   bool
}

func TestBarcodeWebSocketAcknowledgement(t *testing.T) {
	kb := &countingKeyboard{}
	host := httptest.NewServer(New(kb, "ws://127.0.0.1:8080/ws").Handler())
	defer host.Close()
	conn, _, err := websocket.DefaultDialer.Dial("ws"+strings.TrimPrefix(host.URL, "http")+"/ws", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	var hello map[string]any
	if err := conn.ReadJSON(&hello); err != nil || hello["type"] != "hello" {
		t.Fatalf("bridge hello: %v %+v", err, hello)
	}
	if err := conn.WriteJSON(Payload{Type: "barcode", ID: "one", Value: "123456"}); err != nil {
		t.Fatal(err)
	}
	var ack barcodeAck
	if err := conn.ReadJSON(&ack); err != nil || !ack.Success || ack.ID != "one" {
		t.Fatalf("barcode ack: %v %+v", err, ack)
	}
	if len(kb.values) != 1 || kb.values[0] != "123456" {
		t.Fatalf("keyboard values: %v", kb.values)
	}
}

func (k *countingKeyboard) Type(value string) error {
	if k.fail {
		return errors.New("keyboard unavailable")
	}
	k.values = append(k.values, value)
	return nil
}

func (k *countingKeyboard) Enter() error { return nil }

func TestBarcodeAcknowledgementAndRetry(t *testing.T) {
	kb := &countingKeyboard{}
	s := New(kb, "ws://127.0.0.1:8080/ws")
	p := Payload{Type: "barcode", ID: "scan-1", Value: "8991234567890"}
	first := s.injectBarcode(p, true, "phone")
	second := s.injectBarcode(p, true, "phone")
	if !first.Success || first.Type != "barcode_ack" || second != first || len(kb.values) != 1 {
		t.Fatalf("ack/dedup failed: first=%+v second=%+v typed=%v", first, second, kb.values)
	}
	kb.fail = true
	failed := s.injectBarcode(Payload{Type: "barcode", ID: "scan-2", Value: "BAD"}, true, "phone")
	if failed.Success || failed.Error == "" {
		t.Fatalf("expected failed acknowledgement: %+v", failed)
	}
	kb.fail = false
	repeated := s.injectBarcode(Payload{Type: "barcode", ID: "scan-2", Value: "BAD"}, true, "phone")
	if repeated.Success || len(kb.values) != 1 {
		t.Fatalf("failed scan was repeated with the same ID: %+v typed=%v", repeated, kb.values)
	}
	retried := s.injectBarcode(Payload{Type: "barcode", ID: "scan-3", Value: "BAD"}, true, "phone")
	if !retried.Success || len(kb.values) != 2 {
		t.Fatalf("explicit retry failed: %+v typed=%v", retried, kb.values)
	}
}

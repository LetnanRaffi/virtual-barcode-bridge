package server

import (
	"bytes"
	"encoding/json"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/skip2/go-qrcode"
	"virtual-barcode-bridge/network"
)

func TestNetworkQRSelection(t *testing.T) {
	options := []network.Option{
		{Label: "Ethernet — 172.16.1.2", URL: "ws://172.16.1.2:8080/ws"},
		{Label: "Wi-Fi — 192.168.1.2", URL: "ws://192.168.1.2:8080/ws"},
	}
	handler := New(nil, options[0].URL, options...).Handler()
	list := httptest.NewRecorder()
	handler.ServeHTTP(list, httptest.NewRequest("GET", "/networks", nil))
	var got []network.Option
	if err := json.Unmarshal(list.Body.Bytes(), &got); err != nil || len(got) != 2 || got[1] != options[1] {
		t.Fatalf("network choices: %s (%v)", list.Body.String(), err)
	}
	for _, option := range options {
		response := httptest.NewRecorder()
		handler.ServeHTTP(response, httptest.NewRequest("GET", "/qr.png?endpoint="+url.QueryEscape(option.URL), nil))
		want, _ := qrcode.Encode(option.URL, qrcode.Medium, 384)
		if response.Code != 200 || !bytes.Equal(response.Body.Bytes(), want) {
			t.Fatalf("wrong QR for %s: status %d", option.Label, response.Code)
		}
	}
	bad := httptest.NewRecorder()
	handler.ServeHTTP(bad, httptest.NewRequest("GET", "/qr.png?endpoint=ws://unknown/ws", nil))
	if bad.Code != 400 {
		t.Fatalf("unknown network accepted: %d", bad.Code)
	}
}

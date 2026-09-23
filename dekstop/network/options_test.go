package network

import "testing"

func TestInterfaceLabelsAndEndpoint(t *testing.T) {
	if got := interfaceLabel("Remote NDIS USB Device"); got != "Likely USB/Tethering · Remote NDIS USB Device" {
		t.Fatalf("tether label = %q", got)
	}
	if got := interfaceLabel("Wi-Fi"); got != "Wi-Fi · Wi-Fi" {
		t.Fatalf("Wi-Fi label = %q", got)
	}
	if got := interfaceLabel("wlp1s0"); got != "Wi-Fi · wlp1s0" {
		t.Fatalf("Linux Wi-Fi label = %q", got)
	}
	if got := endpoint("192.168.42.10", 8080); got != "ws://192.168.42.10:8080/ws" {
		t.Fatalf("endpoint = %q", got)
	}
}

func TestInterfaceLabelDoesNotClaimUnknownAdapterIsUSB(t *testing.T) {
	if got := interfaceLabel("bridge0"); got != "Network · bridge0" {
		t.Fatalf("unknown adapter label = %q", got)
	}
}

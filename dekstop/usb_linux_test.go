//go:build linux

package main

import "testing"

func TestParseUSBDevices(t *testing.T) {
	output := "List of devices attached\n" +
		"ABC123 device usb:1-2 product:phone model:Pixel_7 device:panther\n" +
		"DEF456 unauthorized usb:1-3\n" +
		"192.168.1.9:5555 device product:remote model:Remote\n"
	devices, unauthorized := parseUSBDevices(output)
	if !unauthorized || len(devices) != 1 || devices[0].Serial != "ABC123" || devices[0].Label != "Pixel_7 · ABC123" {
		t.Fatalf("unexpected devices: %+v unauthorized=%v", devices, unauthorized)
	}
}

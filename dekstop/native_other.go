//go:build !windows

package main

import (
	"log"
	"virtual-barcode-bridge/network"
)

// openNativeWindow is only implemented on Windows.
func openNativeWindow(_ string, _ ...network.Option) bool {
	return false
}

func updateNativeNetworks(_ []network.Option) {}

func fatalDesktop(message string) { log.Fatal(message) }

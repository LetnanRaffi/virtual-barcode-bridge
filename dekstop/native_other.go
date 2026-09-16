//go:build !windows

package main

import "virtual-barcode-bridge/network"

// openNativeWindow is only implemented on Windows.
func openNativeWindow(_ string, _ ...network.Option) bool {
	return false
}

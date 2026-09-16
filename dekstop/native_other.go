//go:build !windows

package main

// openNativeWindow is only implemented on Windows.
func openNativeWindow(_ string) bool {
	return false
}

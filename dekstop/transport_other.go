//go:build !windows

package main

import "context"

type noopTransport struct{}
func startPlatformUSBTransport(context.Context) transportController { return noopTransport{} }
func (noopTransport) Close() {}

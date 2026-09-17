//go:build windows

package main

import (
	"context"
	"log"
	"virtual-barcode-bridge/adbbridge"
)

type windowsTransport struct { cancel context.CancelFunc }

func startPlatformUSBTransport(parent context.Context) transportController {
	ctx, cancel := context.WithCancel(parent)
	m := &adbbridge.Manager{HostPort: usbPort, OnStatus: func(s adbbridge.Status) {
		log.Printf("USB/ADB: %s (%s)", s.State, s.Detail)
		setNativeUSBStatus(string(s.State), s.Detail)
	}}
	go m.Run(ctx)
	return &windowsTransport{cancel: cancel}
}

func (t *windowsTransport) Close() { t.cancel() }

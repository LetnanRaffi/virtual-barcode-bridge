package main

import "context"

const usbPort = 8765

type transportController interface { Close() }

func startUSBTransport(ctx context.Context) transportController { return startPlatformUSBTransport(ctx) }

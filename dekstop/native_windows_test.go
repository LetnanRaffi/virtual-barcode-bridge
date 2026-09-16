//go:build windows

package main

import (
	"github.com/gorilla/websocket"
	"net/http/httptest"
	"runtime"
	"strings"
	"syscall"
	"testing"
	"time"
	"unsafe"
	"virtual-barcode-bridge/keyboard"
	"virtual-barcode-bridge/network"
	"virtual-barcode-bridge/server"
)

// Run on Windows: exercises DLL resolution, synchronous WM_PAINT, and closing
// the window on its owning thread without terminating the test process.
func TestNativeWindowLifecycle(t *testing.T) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	defer cleanupQR()

	for _, proc := range []*syscall.LazyProc{
		procGetModuleHandleW, procSetBkMode, procSetTextColor, procDrawTextW,
	} {
		if err := proc.Find(); err != nil {
			t.Fatalf("resolve %s: %v", proc.Name, err)
		}
	}
	if !createNativeWindow("ws://172.16.1.2:8080/ws",
		network.Option{Label: "Ethernet", URL: "ws://172.16.1.2:8080/ws"},
		network.Option{Label: "Wi-Fi", URL: "ws://192.168.1.2:8080/ws"}) {
		t.Fatal("native window creation failed")
	}
	class, _ := syscall.UTF16PtrFromString(wndClassName)
	hwnd, _, err := user32.NewProc("FindWindowW").Call(uintptr(unsafe.Pointer(class)), 0)
	if hwnd == 0 {
		t.Fatalf("find native window: %v", err)
	}
	defer procDestroyWindow.Call(hwnd)
	if ok, _, err := user32.NewProc("UpdateWindow").Call(hwnd); ok == 0 {
		t.Fatalf("paint native window: %v", err)
	}
	send := user32.NewProc("SendMessageW")
	send.Call(networkCombo, 0x014E, 1, 0)
	send.Call(hwnd, 0x0111, 101|(1<<16), networkCombo)
	if got := syscall.UTF16ToString(urlUTF); got != "ws://192.168.1.2:8080/ws" {
		t.Fatalf("network selection did not change QR endpoint: %s", got)
	}
	if ok, _, err := user32.NewProc("UpdateWindow").Call(hwnd); ok == 0 {
		t.Fatalf("repaint selected network: %v", err)
	}

	// Exercise the actual WebSocket -> SendInput -> focused Windows edit field.
	editClass, _ := syscall.UTF16PtrFromString("EDIT")
	edit, _, err := procCreateWindowExW.Call(0,
		uintptr(unsafe.Pointer(editClass)), 0,
		0x40000000|wsVisible|0x00800000, 16, 520, 400, 30,
		hwnd, 102, 0, 0)
	if edit == 0 {
		t.Fatalf("create input target: %v", err)
	}
	user32.NewProc("SetForegroundWindow").Call(hwnd)
	user32.NewProc("SetFocus").Call(edit)
	httpServer := httptest.NewServer(server.New(keyboard.New(), "ws://127.0.0.1/ws").Handler())
	defer httpServer.Close()
	conn, _, err := websocket.DefaultDialer.Dial("ws"+strings.TrimPrefix(httpServer.URL, "http")+"/ws", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	if err := conn.WriteJSON(map[string]any{"type": "scan", "data": "VBB-1234567890", "auto_enter": false}); err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(5 * time.Second)
	var received string
	for time.Now().Before(deadline) {
		var message msg
		for {
			ok, _, _ := user32.NewProc("PeekMessageW").Call(uintptr(unsafe.Pointer(&message)), 0, 0, 0, 1)
			if ok == 0 {
				break
			}
			procTranslateMessage.Call(uintptr(unsafe.Pointer(&message)))
			procDispatchMessageW.Call(uintptr(unsafe.Pointer(&message)))
		}
		var text [256]uint16
		user32.NewProc("GetWindowTextW").Call(edit, uintptr(unsafe.Pointer(&text[0])), uintptr(len(text)))
		received = syscall.UTF16ToString(text[:])
		if received == "VBB-1234567890" {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	if received != "VBB-1234567890" {
		t.Fatalf("WebSocket keyboard injection: got %q", received)
	}
	t.Log("native creation, paint, network switch, WebSocket keyboard injection and close")
	// Posting WM_CLOSE and pumping on this thread must reach WM_QUIT.
	if ok, _, err := user32.NewProc("PostMessageW").Call(hwnd, wmClose, 0, 0); ok == 0 {
		t.Fatalf("post close: %v", err)
	}
	pumpMessages()
}

//go:build windows

package main

import (
	"log"
	"os"
	"runtime"
	"sync"
	"syscall"
	"unsafe"

	"github.com/skip2/go-qrcode"
	"virtual-barcode-bridge/network"
)

var (
	user32 = syscall.NewLazyDLL("user32.dll")
	gdi32  = syscall.NewLazyDLL("gdi32.dll")
	k32    = syscall.NewLazyDLL("kernel32.dll")

	procRegisterClassExW = user32.NewProc("RegisterClassExW")
	procCreateWindowExW  = user32.NewProc("CreateWindowExW")
	procShowWindow       = user32.NewProc("ShowWindow")
	procGetMessageW      = user32.NewProc("GetMessageW")
	procTranslateMessage = user32.NewProc("TranslateMessage")
	procDispatchMessageW = user32.NewProc("DispatchMessageW")
	procDefWindowProcW   = user32.NewProc("DefWindowProcW")
	procDestroyWindow    = user32.NewProc("DestroyWindow")
	procPostQuitMessage  = user32.NewProc("PostQuitMessage")
	procGetModuleHandleW = k32.NewProc("GetModuleHandleW")
	procLoadCursorW      = user32.NewProc("LoadCursorW")
	procBeginPaint       = user32.NewProc("BeginPaint")
	procEndPaint         = user32.NewProc("EndPaint")
	procGetClientRect    = user32.NewProc("GetClientRect")
	procDrawTextW        = user32.NewProc("DrawTextW")
	procSetBkMode        = gdi32.NewProc("SetBkMode")
	procSetTextColor     = gdi32.NewProc("SetTextColor")

	procCreateCompatibleDC = gdi32.NewProc("CreateCompatibleDC")
	procDeleteDC           = gdi32.NewProc("DeleteDC")
	procCreateDIBSection   = gdi32.NewProc("CreateDIBSection")
	procDeleteObject       = gdi32.NewProc("DeleteObject")
	procSelectObject       = gdi32.NewProc("SelectObject")
	procStretchBlt         = gdi32.NewProc("StretchBlt")
	procGetStockObject     = gdi32.NewProc("GetStockObject")
)

const (
	csHRedraw    = 0x0001
	csVRedraw    = 0x0002
	wsOverlapped = 0x00CF0000
	wsVisible    = 0x10000000
	cwUseDefault = 0x80000000
	wndClassName = "VirtualBarcodeBridge"
	idcArrow     = 32512
	blackBrush   = 4
	defaultFont  = 17

	wmClose     = 0x0010
	wmDestroy   = 0x0002
	wmPaint     = 0x000F
	wmEraseBack = 0x0014

	dibRGBColors = 0
	srcCopy      = 0x00CC0020
	transparent  = 1

	swShowDefault = 10
)

var (
	memDC          uintptr
	qrmem          uintptr
	oldBitmap      uintptr
	qrW, qrH       int32
	urlUTF         []uint16
	wndProcPtr     uintptr
	networkCombo   uintptr
	networkOptions []network.Option
	usbStatusMu    sync.RWMutex
	usbStatusText  = "USB / ADB: starting…"
	nativeWindow   uintptr
)

func setNativeUSBStatus(state, detail string) {
	usbStatusMu.Lock()
	switch state {
	case "connected": usbStatusText = "USB / ADB: connected — tunnel ready"
	case "unauthorized": usbStatusText = "USB / ADB: authorize this PC on Android"
	case "no_device": usbStatusText = "USB / ADB: connect phone + enable USB debugging"
	case "adb_missing": usbStatusText = "USB / ADB: adb.exe not found beside app"
	default: usbStatusText = "USB / ADB: " + detail
	}
	usbStatusMu.Unlock()
	if nativeWindow != 0 {
		user32.NewProc("InvalidateRect").Call(nativeWindow, 0, 1)
	}
}

type point struct{ x, y int32 }

type rect struct{ left, top, right, bottom int32 }

type wndClassEx struct {
	cbSize     uint32
	style      uint32
	wndProc    uintptr
	cbClsExtra int32
	cbWndExtra int32
	hInstance  uintptr
	hIcon      uintptr
	hCursor    uintptr
	hBrush     uintptr
	menuName   *uint16
	className  *uint16
	hIconSm    uintptr
}

type msg struct {
	hwnd    uintptr
	message uint32
	_       uint32
	wparam  uintptr
	lparam  uintptr
	time    uint32
	pt      point
}

type paintStruct struct {
	hDC      uintptr
	fErase   uint32
	rcPaint  rect
	fRefresh uint32
	fInc     uint32
	rgb      [32]byte
}

type bitmapInfoHeader struct {
	biSize          uint32
	biWidth         int32
	biHeight        int32
	biPlanes        uint16
	biBitCount      uint16
	biCompression   uint32
	biSizeImage     uint32
	biXPelsPerMeter int32
	biYPelsPerMeter int32
	biClrUsed       uint32
	biClrImportant  uint32
}

type bitmapInfo struct {
	header bitmapInfoHeader
	_      [1]uint32
}

// Creation, painting and the message loop must share one OS thread.
func openNativeWindow(wsURL string, options ...network.Option) bool {
	ready := make(chan bool, 1)
	go func() {
		runtime.LockOSThread()
		defer runtime.UnlockOSThread()
		defer cleanupQR()
		if !createNativeWindow(wsURL, options...) {
			ready <- false
			return
		}
		ready <- true
		pumpMessages()
		cleanupQR()
		os.Exit(0)
	}()
	return <-ready
}

func createNativeWindow(wsURL string, options ...network.Option) bool {
	networkOptions = options
	if len(networkOptions) == 0 {
		networkOptions = []network.Option{{Label: wsURL, URL: wsURL}}
	}
	u, err := syscall.UTF16FromString(wsURL)
	if err != nil {
		return false
	}
	urlUTF = u

	if err := buildQRBits(wsURL); err != nil || qrW == 0 {
		return false
	}

	if !registerClass() {
		return false
	}
	hInst, _, _ := procGetModuleHandleW.Call(0)

	title, _ := syscall.UTF16PtrFromString("Virtual Barcode Bridge")
	class, _ := syscall.UTF16PtrFromString(wndClassName)
	hwnd, _, callErr := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(class)),
		uintptr(unsafe.Pointer(title)),
		wsOverlapped|wsVisible|0x02000000,
		cwUseDefault, cwUseDefault,
		460, 640,
		0, 0, hInst, 0)
	if hwnd == 0 {
		log.Printf("create native window: %v", callErr)
		return false
	}
	nativeWindow = hwnd
	comboClass, _ := syscall.UTF16PtrFromString("COMBOBOX")
	networkCombo, _, callErr = procCreateWindowExW.Call(
		0, uintptr(unsafe.Pointer(comboClass)), 0,
		0x40000000|wsVisible|0x00200000|0x00010000|3,
		16, 16, 410, 240, hwnd, 101, hInst, 0)
	if networkCombo == 0 {
		log.Printf("create network selector: %v", callErr)
		procDestroyWindow.Call(hwnd)
		return false
	}
	send := user32.NewProc("SendMessageW")
	font, _, _ := procGetStockObject.Call(defaultFont)
	send.Call(networkCombo, 0x0030, font, 1)
	for i, option := range networkOptions {
		label, _ := syscall.UTF16PtrFromString(option.Label)
		send.Call(networkCombo, 0x0143, 0, uintptr(unsafe.Pointer(label)))
		if option.URL == wsURL {
			send.Call(networkCombo, 0x014E, uintptr(i), 0)
		}
	}
	procShowWindow.Call(hwnd, swShowDefault)

	return true
}

func buildQRBits(content string) error {
	q, err := qrcode.New(content, qrcode.Medium)
	if err != nil {
		return err
	}
	img := q.Image(360)
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	qw, qh := int32(w), int32(h)
	px := make([]byte, qw*qh*4)
	for y := 0; y < int(qh); y++ {
		for x := 0; x < int(qw); x++ {
			r, g, b, _ := img.At(x, y).RGBA()
			i := (y*int(qw) + x) * 4
			px[i] = byte(b >> 8)
			px[i+1] = byte(g >> 8)
			px[i+2] = byte(r >> 8)
			px[i+3] = 255
		}
	}

	dc, _, _ := procCreateCompatibleDC.Call(0)
	if dc == 0 {
		return syscall.EPERM
	}
	bi := &bitmapInfo{}
	bh := &bi.header
	bh.biSize = uint32(unsafe.Sizeof(bitmapInfoHeader{}))
	bh.biWidth = qw
	bh.biHeight = -qh
	bh.biPlanes = 1
	bh.biBitCount = 32
	bh.biCompression = 0
	bh.biSizeImage = uint32(qw * qh * 4)

	var bits uintptr
	bmp, _, _ := procCreateDIBSection.Call(
		dc,
		uintptr(unsafe.Pointer(bi)),
		dibRGBColors,
		uintptr(unsafe.Pointer(&bits)),
		0, 0)
	if bmp == 0 || bits == 0 {
		procDeleteDC.Call(dc)
		return syscall.EPERM
	}
	dst := unsafe.Slice((*byte)(unsafe.Pointer(bits)), qw*qh*4)
	copy(dst, px)

	oldBitmap, _, _ = procSelectObject.Call(dc, bmp)
	memDC = dc
	qrmem = bmp
	qrW, qrH = qw, qh
	return nil
}

func registerClass() bool {
	hInst, _, _ := procGetModuleHandleW.Call(0)
	cursor, _, _ := procLoadCursorW.Call(0, idcArrow)
	brush, _, _ := procGetStockObject.Call(blackBrush)
	class, _ := syscall.UTF16PtrFromString(wndClassName)

	wndProcPtr = syscall.NewCallback(wndProc)
	wc := &wndClassEx{
		cbSize:    uint32(unsafe.Sizeof(wndClassEx{})),
		style:     csHRedraw | csVRedraw,
		wndProc:   wndProcPtr,
		hInstance: hInst,
		hCursor:   cursor,
		hBrush:    brush,
		className: class,
	}
	atom, _, callErr := procRegisterClassExW.Call(uintptr(unsafe.Pointer(wc)))
	// atom = class atom (nonzero on success). err reflects GetLastError, which
	// may be stale after a successful call, so rely on the atom.
	if atom == 0 {
		log.Printf("register native window: %v", callErr)
	}
	return atom != 0
}

func wndProc(hwnd uintptr, uMsg uint32, wParam, lParam uintptr) uintptr {
	switch uMsg {
	case 0x0005: // WM_SIZE
		if networkCombo != 0 {
			width := int32(lParam&0xffff) - 32
			if width < 80 {
				width = 80
			}
			user32.NewProc("MoveWindow").Call(networkCombo, 16, 16, uintptr(width), 240, 1)
		}
	case 0x0111: // WM_COMMAND / CBN_SELCHANGE
		if wParam&0xffff == 101 && (wParam>>16)&0xffff == 1 {
			index, _, _ := user32.NewProc("SendMessageW").Call(networkCombo, 0x0147, 0, 0)
			if index < uintptr(len(networkOptions)) {
				endpoint := networkOptions[index].URL
				oldDC, oldQR, oldSelected := memDC, qrmem, oldBitmap
				if err := buildQRBits(endpoint); err != nil {
					log.Printf("switch network: %v", err)
				} else {
					newDC, newQR, newSelected := memDC, qrmem, oldBitmap
					memDC, qrmem, oldBitmap = oldDC, oldQR, oldSelected
					cleanupQR()
					memDC, qrmem, oldBitmap = newDC, newQR, newSelected
					urlUTF, _ = syscall.UTF16FromString(endpoint)
					user32.NewProc("InvalidateRect").Call(hwnd, 0, 1)
				}
			}
			return 0
		}
	case wmClose:
		procDestroyWindow.Call(hwnd)
		return 0
	case wmDestroy:
		procPostQuitMessage.Call(0)
		return 0
	case wmPaint:
		drawWindow(hwnd)
		return 0
	}
	ret, _, _ := procDefWindowProcW.Call(hwnd, uintptr(uMsg), wParam, lParam)
	return ret
}

func drawWindow(hwnd uintptr) {
	var ps paintStruct
	hdc, _, _ := procBeginPaint.Call(hwnd, uintptr(unsafe.Pointer(&ps)))
	defer procEndPaint.Call(hwnd, uintptr(unsafe.Pointer(&ps)))
	var cr rect
	procGetClientRect.Call(hwnd, uintptr(unsafe.Pointer(&cr)))

	if hdc == 0 {
		return
	}
	cw := cr.right - cr.left
	ch := cr.bottom - cr.top

	margin := cw / 12
	if margin < 10 {
		margin = 10
	}
	top := int32(64)
	qsize := cw - 2*margin
	if qsize > ch-top-80 {
		qsize = ch - top - 80
	}

	if qsize <= 0 {
		return
	}
	procStretchBlt.Call(
		hdc,
		uintptr(margin), uintptr(top), uintptr(qsize), uintptr(qsize),
		memDC, 0, 0, uintptr(qrW), uintptr(qrH), srcCopy)

	font, _, _ := procGetStockObject.Call(defaultFont)
	procSelectObject.Call(hdc, font)
	procSetBkMode.Call(hdc, transparent)

	green := uintptr(0x0094ca3f) // BGR green
	procSetTextColor.Call(hdc, green)
	tr := rect{margin, top + qsize + 6, cw - margin, top + qsize + 34}
	procDrawTextW.Call(
		hdc,
		uintptr(unsafe.Pointer(&urlUTF[0])), ^uintptr(0), // -1 for text length
		uintptr(unsafe.Pointer(&tr)),
		dtSingleLine|dtCenter|dtVCenter)

	procSetTextColor.Call(hdc, 0x00e6e8ee)
	tr = rect{0, tr.bottom + 2, cw, tr.bottom + 30}
	t2, _ := syscall.UTF16PtrFromString("Choose Wi-Fi / LAN above, then scan with the app")
	procDrawTextW.Call(
		hdc,
		uintptr(unsafe.Pointer(t2)), ^uintptr(0),
		uintptr(unsafe.Pointer(&tr)),
		dtSingleLine|dtCenter|dtVCenter)

	usbStatusMu.RLock()
	status := usbStatusText
	usbStatusMu.RUnlock()
	statusUTF, _ := syscall.UTF16PtrFromString(status)
	tr = rect{0, tr.bottom + 6, cw, tr.bottom + 36}
	procSetTextColor.Call(hdc, 0x0094ca3f)
	procDrawTextW.Call(hdc, uintptr(unsafe.Pointer(statusUTF)), ^uintptr(0),
		uintptr(unsafe.Pointer(&tr)), dtSingleLine|dtCenter|dtVCenter)

}

func pumpMessages() {
	var m msg
	for {
		ret, _, callErr := procGetMessageW.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
		if int32(ret) == -1 {
			log.Printf("native message loop: %v", callErr)
			break
		}
		if ret == 0 {
			break
		}
		procTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		procDispatchMessageW.Call(uintptr(unsafe.Pointer(&m)))
	}
}

func cleanupQR() {
	if memDC != 0 {
		if oldBitmap != 0 {
			procSelectObject.Call(memDC, oldBitmap)
		}
		if qrmem != 0 {
			procDeleteObject.Call(qrmem)
		}
		procDeleteDC.Call(memDC)
	}
	memDC, qrmem, oldBitmap = 0, 0, 0
}

const (
	dtCenter     = 0x0001
	dtVCenter    = 0x0004
	dtSingleLine = 0x0020
)

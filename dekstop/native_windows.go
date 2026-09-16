//go:build windows

package main

import (
	"os"
	"syscall"
	"unsafe"

	"github.com/skip2/go-qrcode"
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
	procGetModuleHandleW = user32.NewProc("GetModuleHandleW")
	procLoadCursorW      = user32.NewProc("LoadCursorW")
	procBeginPaint       = user32.NewProc("BeginPaint")
	procEndPaint         = user32.NewProc("EndPaint")
	procGetClientRect    = user32.NewProc("GetClientRect")
	procDrawTextW        = user32.NewProc("DrawTextW")
	procSetBkMode        = user32.NewProc("SetBkMode")
	procSetTextColor     = user32.NewProc("SetTextColor")

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
	winURL     string
	winHwnd    uintptr
	memDC      uintptr
	qrmem      uintptr
	qrW, qrH   int32
	urlUTF     []uint16
	wndProcPtr uintptr
)

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

// openNativeWindow draws the QR + connection URL in a native GDI window and
// pumps messages until the window is closed, then exits the process.
func openNativeWindow(wsURL string) bool {
	winURL = wsURL
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
	hwnd, _, _ := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(class)),
		uintptr(unsafe.Pointer(title)),
		wsOverlapped|wsVisible,
		cwUseDefault, cwUseDefault,
		460, 640,
		0, 0, hInst, 0)
	if hwnd == 0 {
		return false
	}
	winHwnd = hwnd
	procShowWindow.Call(hwnd, swShowDefault)

	go pumpMessages()
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

	procSelectObject.Call(dc, bmp)
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
	atom, _, _ := procRegisterClassExW.Call(uintptr(unsafe.Pointer(wc)))
	// atom = class atom (nonzero on success). err reflects GetLastError, which
	// may be stale after a successful call, so rely on the atom.
	return atom != 0
}

func wndProc(hwnd uintptr, uMsg uint32, wParam, lParam uintptr) uintptr {
	switch uMsg {
	case wmEraseBack:
		return 1
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
	var cr rect
	procGetClientRect.Call(hwnd, uintptr(unsafe.Pointer(&cr)))

	cw := cr.right - cr.left
	ch := cr.bottom - cr.top

	margin := cw / 12
	if margin < 10 {
		margin = 10
	}
	top := ch / 16
	qsize := cw - 2*margin
	if qsize > ch-80 {
		qsize = ch - 80
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
		uintptr(unsafe.Pointer(&urlUTF[0])), ^uintptr(0), // -1 for text length
		uintptr(unsafe.Pointer(&tr)),
		dtSingleLine|dtCenter|dtVCenter)

	procSetTextColor.Call(hdc, 0x00e6e8ee)
	tr = rect{0, tr.bottom + 2, cw, tr.bottom + 30}
	t2, _ := syscall.UTF16PtrFromString("Scan with your phone camera")
	procDrawTextW.Call(
		uintptr(unsafe.Pointer(t2)), ^uintptr(0),
		uintptr(unsafe.Pointer(&tr)),
		dtSingleLine|dtCenter|dtVCenter)

	procEndPaint.Call(hwnd, uintptr(unsafe.Pointer(&ps)))
}

func pumpMessages() {
	var m msg
	for {
		ret, _, _ := procGetMessageW.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
		if int32(ret) <= 0 {
			break
		}
		procTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		procDispatchMessageW.Call(uintptr(unsafe.Pointer(&m)))
	}
	procDeleteObject.Call(qrmem)
	procDeleteDC.Call(memDC)
	os.Exit(0)
}

const (
	dtCenter     = 0x0001
	dtVCenter    = 0x0004
	dtSingleLine = 0x0020
)

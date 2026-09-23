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
	procCreateSolidBrush = gdi32.NewProc("CreateSolidBrush")
	procFillRect         = user32.NewProc("FillRect")
	procRoundRect        = gdi32.NewProc("RoundRect")
	procCreateFontW      = gdi32.NewProc("CreateFontW")

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
	wmLButtonUp = 0x0202
	wmNetworks  = 0x8001
	wmUSB       = 0x8002

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
	deviceCombo    uintptr
	mainHwnd       uintptr
	networkOptions []network.Option
	usbDevices     []usbDevice
	usbStatus      = "Sambungkan HP dengan kabel USB."
	usbSelected    string
	mode           int // 0: choose, 1: USB ADB, 2: network
	uiMu           sync.Mutex
	pendingOptions []network.Option
	pendingDevices []usbDevice
	pendingStatus  string
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
		if usb != nil {
			usb.Close()
		}
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

	title, _ := syscall.UTF16PtrFromString("ScanBridge")
	class, _ := syscall.UTF16PtrFromString(wndClassName)
	hwnd, _, callErr := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(class)),
		uintptr(unsafe.Pointer(title)),
		wsOverlapped|wsVisible|0x02000000,
		cwUseDefault, cwUseDefault,
		720, 650,
		0, 0, hInst, 0)
	if hwnd == 0 {
		log.Printf("create native window: %v", callErr)
		return false
	}
	mainHwnd = hwnd
	mode = 0
	comboClass, _ := syscall.UTF16PtrFromString("COMBOBOX")
	networkCombo, _, callErr = procCreateWindowExW.Call(
		0, uintptr(unsafe.Pointer(comboClass)), 0,
		0x40000000|wsVisible|0x00200000|0x00010000|3,
		32, 300, 340, 240, hwnd, 101, hInst, 0)
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
	deviceCombo, _, _ = procCreateWindowExW.Call(
		0, uintptr(unsafe.Pointer(comboClass)), 0,
		0x40000000|0x00200000|0x00010000|3,
		32, 300, 340, 240, hwnd, 102, hInst, 0)
	if deviceCombo != 0 {
		send.Call(deviceCombo, 0x0030, font, 1)
	}
	procShowWindow.Call(networkCombo, 0)
	procShowWindow.Call(hwnd, swShowDefault)

	return true
}

func updateNativeNetworks(options []network.Option) {
	uiMu.Lock()
	pendingOptions = append([]network.Option(nil), options...)
	uiMu.Unlock()
	if mainHwnd != 0 {
		user32.NewProc("PostMessageW").Call(mainHwnd, wmNetworks, 0, 0)
	}
}

func updateNativeUSB(status string, devices []usbDevice) {
	uiMu.Lock()
	pendingStatus = status
	pendingDevices = append([]usbDevice(nil), devices...)
	uiMu.Unlock()
	if mainHwnd != 0 {
		user32.NewProc("PostMessageW").Call(mainHwnd, wmUSB, 0, 0)
	}
}

func fatalDesktop(message string) {
	wide, _ := syscall.UTF16PtrFromString(message)
	title, _ := syscall.UTF16PtrFromString("Barcode Bridge")
	user32.NewProc("MessageBoxW").Call(0, uintptr(unsafe.Pointer(wide)), uintptr(unsafe.Pointer(title)), 0x10)
	log.Fatal(message)
}

func refreshNativeQR(hwnd uintptr, endpoint string) {
	oldDC, oldQR, oldSelected := memDC, qrmem, oldBitmap
	if err := buildQRBits(endpoint); err != nil {
		log.Printf("switch network: %v", err)
		return
	}
	newDC, newQR, newSelected := memDC, qrmem, oldBitmap
	memDC, qrmem, oldBitmap = oldDC, oldQR, oldSelected
	cleanupQR()
	memDC, qrmem, oldBitmap = newDC, newQR, newSelected
	urlUTF, _ = syscall.UTF16FromString(endpoint)
	user32.NewProc("InvalidateRect").Call(hwnd, 0, 1)
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
		user32.NewProc("InvalidateRect").Call(hwnd, 0, 1)
	case wmLButtonUp:
		x, y := int32(int16(lParam&0xffff)), int32(int16((lParam>>16)&0xffff))
		if y >= 112 && y <= 214 {
			if x >= 32 && x <= 344 {
				mode = 1
			} else if x >= 370 && x <= 682 {
				mode = 2
			}
			procShowWindow.Call(networkCombo, map[bool]uintptr{true: 5, false: 0}[mode == 2])
			procShowWindow.Call(deviceCombo, map[bool]uintptr{true: 5, false: 0}[mode == 1])
			if usb != nil {
				usb.SetActive(mode == 1)
			}
			user32.NewProc("InvalidateRect").Call(hwnd, 0, 1)
		}
	case 0x0111: // WM_COMMAND / CBN_SELCHANGE
		if wParam&0xffff == 101 && (wParam>>16)&0xffff == 1 {
			index, _, _ := user32.NewProc("SendMessageW").Call(networkCombo, 0x0147, 0, 0)
			if index < uintptr(len(networkOptions)) {
				refreshNativeQR(hwnd, networkOptions[index].URL)
			}
			return 0
		}
		if wParam&0xffff == 102 && (wParam>>16)&0xffff == 1 {
			index, _, _ := user32.NewProc("SendMessageW").Call(deviceCombo, 0x0147, 0, 0)
			if index < uintptr(len(usbDevices)) && usb != nil {
				usbSelected = usbDevices[index].Serial
				usb.Select(usbDevices[index].Serial)
			}
			return 0
		}
	case wmNetworks:
		uiMu.Lock()
		networkOptions = append([]network.Option(nil), pendingOptions...)
		uiMu.Unlock()
		selected := syscall.UTF16ToString(urlUTF)
		send := user32.NewProc("SendMessageW")
		send.Call(networkCombo, 0x014B, 0, 0)
		chosen := -1
		for i, option := range networkOptions {
			label, _ := syscall.UTF16PtrFromString(option.Label)
			send.Call(networkCombo, 0x0143, 0, uintptr(unsafe.Pointer(label)))
			if option.URL == selected {
				chosen = i
			}
		}
		if chosen < 0 && len(networkOptions) > 0 {
			chosen = 0
		}
		if chosen >= 0 {
			send.Call(networkCombo, 0x014E, uintptr(chosen), 0)
			if selected != networkOptions[chosen].URL {
				refreshNativeQR(hwnd, networkOptions[chosen].URL)
			}
		}
		user32.NewProc("InvalidateRect").Call(hwnd, 0, 1)
		return 0
	case wmUSB:
		uiMu.Lock()
		usbStatus = pendingStatus
		usbDevices = append([]usbDevice(nil), pendingDevices...)
		uiMu.Unlock()
		send := user32.NewProc("SendMessageW")
		send.Call(deviceCombo, 0x014B, 0, 0)
		selectedIndex := -1
		for i, device := range usbDevices {
			label, _ := syscall.UTF16PtrFromString(device.Label)
			send.Call(deviceCombo, 0x0143, 0, uintptr(unsafe.Pointer(label)))
			if device.Serial == usbSelected {
				selectedIndex = i
			}
		}
		if selectedIndex < 0 && len(usbDevices) == 1 {
			selectedIndex = 0
			usbSelected = usbDevices[0].Serial
		}
		if selectedIndex >= 0 {
			send.Call(deviceCombo, 0x014E, uintptr(selectedIndex), 0)
		}
		user32.NewProc("InvalidateRect").Call(hwnd, 0, 1)
		return 0
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
	if hdc == 0 {
		return
	}
	var client rect
	procGetClientRect.Call(hwnd, uintptr(unsafe.Pointer(&client)))
	fill(hdc, client, color(14, 21, 33))
	procSetBkMode.Call(hdc, transparent)
	label(hdc, "SCANBRIDGE", rect{32, 28, 400, 65}, 27, 700, color(245, 248, 255))
	label(hdc, "HP scan barcode, komputer langsung mengetik.", rect{34, 70, 670, 95}, 16, 400, color(170, 187, 210))
	card(hdc, rect{32, 112, 344, 214}, mode == 1)
	card(hdc, rect{370, 112, 682, 214}, mode == 2)
	label(hdc, "USB KABEL", rect{52, 131, 320, 165}, 20, 700, color(245, 248, 255))
	label(hdc, "ADB · tanpa Wi-Fi", rect{52, 170, 320, 195}, 15, 400, color(170, 187, 210))
	label(hdc, "JARINGAN", rect{390, 131, 660, 165}, 20, 700, color(245, 248, 255))
	label(hdc, "Wi-Fi · LAN · USB tethering", rect{390, 170, 660, 195}, 15, 400, color(170, 187, 210))
	if mode == 0 {
		label(hdc, "Pilih cara menghubungkan HP", rect{32, 255, 680, 305}, 23, 700, color(245, 248, 255))
		label(hdc, "Klik salah satu pilihan di atas untuk memulai.", rect{32, 310, 680, 350}, 16, 400, color(170, 187, 210))
		return
	}
	if mode == 1 {
		label(hdc, "Hubungkan lewat USB", rect{32, 246, 680, 280}, 22, 700, color(245, 248, 255))
		label(hdc, "Perangkat yang terdeteksi", rect{32, 277, 380, 299}, 14, 500, color(170, 187, 210))
		card(hdc, rect{32, 345, 682, 460}, false)
		label(hdc, usbStatus, rect{52, 364, 658, 404}, 17, 600, color(126, 190, 255))
		label(hdc, "1. Aktifkan USB debugging di HP dan izinkan komputer ini.", rect{52, 406, 650, 431}, 15, 400, color(190, 203, 223))
		label(hdc, "2. Buka Barcode Bridge di HP; koneksi berjalan otomatis.", rect{52, 432, 650, 456}, 15, 400, color(190, 203, 223))
		label(hdc, "HP tidak muncul? Pilih Jaringan dan coba USB tethering.", rect{32, 486, 680, 518}, 15, 400, color(170, 187, 210))
		return
	}
	label(hdc, "Hubungkan lewat jaringan", rect{32, 246, 680, 280}, 22, 700, color(245, 248, 255))
	label(hdc, "Pilih adapter yang bisa dijangkau HP", rect{32, 277, 380, 299}, 14, 500, color(170, 187, 210))
	if len(networkOptions) == 0 {
		label(hdc, "Belum ada adapter aktif. Sambungkan Wi-Fi atau nyalakan USB tethering.", rect{32, 350, 680, 400}, 16, 400, color(255, 184, 111))
		return
	}
	white := rect{420, 264, 666, 510}
	fill(hdc, white, color(255, 255, 255))
	procStretchBlt.Call(hdc, 428, 272, 230, 230, memDC, 0, 0, uintptr(qrW), uintptr(qrH), srcCopy)
	label(hdc, syscall.UTF16ToString(urlUTF), rect{32, 356, 398, 420}, 16, 600, color(126, 190, 255))
	label(hdc, "Buka app HP lalu scan QR ini.", rect{32, 435, 390, 470}, 16, 400, color(190, 203, 223))
	label(hdc, "Daftar adapter diperbarui otomatis.", rect{32, 480, 390, 510}, 14, 400, color(170, 187, 210))
}

func color(r, g, b byte) uintptr { return uintptr(r) | uintptr(g)<<8 | uintptr(b)<<16 }

func fill(hdc uintptr, area rect, c uintptr) {
	brush, _, _ := procCreateSolidBrush.Call(c)
	procFillRect.Call(hdc, uintptr(unsafe.Pointer(&area)), brush)
	procDeleteObject.Call(brush)
}

func card(hdc uintptr, area rect, selected bool) {
	c := color(25, 37, 55)
	if selected {
		c = color(39, 69, 105)
	}
	brush, _, _ := procCreateSolidBrush.Call(c)
	oldBrush, _, _ := procSelectObject.Call(hdc, brush)
	pen, _, _ := procGetStockObject.Call(8) // NULL_PEN
	oldPen, _, _ := procSelectObject.Call(hdc, pen)
	procRoundRect.Call(hdc, uintptr(area.left), uintptr(area.top), uintptr(area.right), uintptr(area.bottom), 22, 22)
	procSelectObject.Call(hdc, oldPen)
	procSelectObject.Call(hdc, oldBrush)
	procDeleteObject.Call(brush)
}

func label(hdc uintptr, value string, area rect, size int32, weight int32, c uintptr) {
	face, _ := syscall.UTF16PtrFromString("Segoe UI")
	height := -size
	font, _, _ := procCreateFontW.Call(uintptr(height), 0, 0, 0, uintptr(weight), 0, 0, 0, 1, 0, 0, 5, 0, uintptr(unsafe.Pointer(face)))
	old, _, _ := procSelectObject.Call(hdc, font)
	procSetTextColor.Call(hdc, c)
	wide, _ := syscall.UTF16PtrFromString(value)
	procDrawTextW.Call(hdc, uintptr(unsafe.Pointer(wide)), ^uintptr(0), uintptr(unsafe.Pointer(&area)), 0x0010)
	procSelectObject.Call(hdc, old)
	procDeleteObject.Call(font)
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

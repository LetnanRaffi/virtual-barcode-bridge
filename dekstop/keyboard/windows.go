//go:build windows

package keyboard

import (
	"errors"
	"syscall"
	"unicode/utf16"
	"unsafe"
)

var (
	user32        = syscall.NewLazyDLL("user32.dll")
	procSendInput = user32.NewProc("SendInput")
)

const (
	inputKeyboard       = 1
	keyeventfKeyup      = 0x0002
	keyeventfUnicode    = 0x0004
	vkReturn            = 0x0D
)

type keybdInput struct {
	wVk         uint16
	wScan       uint16
	dwFlags     uint32
	time        uint32
	dwExtraInfo uintptr
}

type input struct {
	typ uint32
	_   uint32
	ki  keybdInput
	_   [8]byte
}

type winInjector struct{}

func newOSInjector() Injector { return &winInjector{} }

func sendInput(items []input) error {
	if len(items) == 0 {
		return nil
	}
	n, _, callErr := procSendInput.Call(
		uintptr(len(items)),
		uintptr(unsafe.Pointer(&items[0])),
		unsafe.Sizeof(input{}),
	)
	if n == 0 {
		msg := "SendInput failed"
		if callErr != nil && callErr.Error() != "" {
			msg += ": " + callErr.Error()
		}
		return errors.New(msg)
	}
	return nil
}

func keyEvent(wVk, wScan uint16, flags uint32) input {
	return input{typ: inputKeyboard, ki: keybdInput{wVk: wVk, wScan: wScan, dwFlags: flags}}
}

func (k *winInjector) Type(text string) error {
	var events []input
	for _, r := range text {
		for _, unit := range utf16.Encode([]rune{r}) {
			events = append(events,
				keyEvent(0, unit, keyeventfUnicode),
				keyEvent(0, unit, keyeventfUnicode|keyeventfKeyup),
			)
		}
	}
	return sendInput(events)
}

func (k *winInjector) Enter() error {
	return sendInput([]input{
		keyEvent(vkReturn, 0, 0),
		keyEvent(vkReturn, 0, keyeventfKeyup),
	})
}
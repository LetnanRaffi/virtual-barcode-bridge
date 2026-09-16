//go:build linux

package keyboard

import (
	"errors"
	"os"
	"os/exec"
)

type linuxInjector struct {
	wayland bool
}

func newOSInjector() Injector {
	return &linuxInjector{wayland: os.Getenv("XDG_SESSION_TYPE") == "wayland"}
}

func xdotool(args ...string) error {
	return exec.Command("xdotool", args...).Run()
}

func (k *linuxInjector) Type(text string) error {
	if k.wayland {
		return errors.New("keystroke injection needs X11 (xdotool); Wayland session unsupported")
	}
	return xdotool("type", "--clearmodifiers", "--", text)
}

func (k *linuxInjector) Enter() error {
	if k.wayland {
		return errors.New("keystroke injection needs X11 (xdotool); Wayland session unsupported")
	}
	return xdotool("key", "Return")
}
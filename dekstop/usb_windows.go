//go:build windows

package main

import (
	"context"
	"crypto/sha256"
	"embed"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"
)

//go:embed usbtools/adb.exe usbtools/AdbWinApi.dll usbtools/AdbWinUsbApi.dll usbtools/NOTICE.txt
var adbFiles embed.FS

type usbDevice struct {
	Serial string
	Label  string
}

type windowsUSB struct {
	mu       sync.Mutex
	active   bool
	selected string
	attached string
	port     int
	adb      string
	cancel   context.CancelFunc
	worker   sync.WaitGroup
}

func hiddenProcess() *syscall.SysProcAttr { return &syscall.SysProcAttr{HideWindow: true} }

func newUSBManager(port int) usbController {
	ctx, cancel := context.WithCancel(context.Background())
	u := &windowsUSB{port: port, cancel: cancel}
	u.worker.Add(1)
	go u.watch(ctx)
	return u
}

func (u *windowsUSB) SetActive(active bool) {
	u.mu.Lock()
	u.active = active
	u.mu.Unlock()
}

func (u *windowsUSB) Select(serial string) {
	u.mu.Lock()
	u.selected = serial
	u.mu.Unlock()
}

func (u *windowsUSB) Close() {
	u.cancel()
	u.worker.Wait()
	u.mu.Lock()
	attached := u.attached
	u.mu.Unlock()
	if attached != "" {
		_, _ = u.run("-s", attached, "reverse", "--remove", "tcp:8080")
	}
	if u.adb != "" {
		_, _ = u.run("kill-server")
	}
}

func (u *windowsUSB) run(args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, u.adb, append([]string{"-P", "16137"}, args...)...)
	cmd.SysProcAttr = hiddenProcess()
	out, err := cmd.CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

func (u *windowsUSB) watch(ctx context.Context) {
	defer u.worker.Done()
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		u.poll()
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func (u *windowsUSB) poll() {
	u.mu.Lock()
	active, selected, attached := u.active, u.selected, u.attached
	u.mu.Unlock()
	if !active {
		if attached != "" && u.adb != "" {
			_, _ = u.run("-s", attached, "reverse", "--remove", "tcp:8080")
			u.mu.Lock()
			u.attached = ""
			u.mu.Unlock()
		}
		return
	}
	if u.adb == "" {
		path, err := extractADB()
		if err != nil {
			updateNativeUSB("ADB tidak dapat dibuka: "+err.Error(), nil)
			return
		}
		u.adb = path
	}
	out, err := u.run("devices", "-l")
	if err != nil {
		updateNativeUSB("ADB gagal. Coba USB tethering atau periksa kebijakan PC.", nil)
		return
	}
	var devices []usbDevice
	unauthorized := false
	for _, line := range strings.Split(out, "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 || strings.HasPrefix(line, "List of") || !strings.Contains(line, "usb:") {
			continue
		}
		if fields[1] == "unauthorized" {
			unauthorized = true
			continue
		}
		if fields[1] != "device" {
			continue
		}
		label := fields[0]
		for _, f := range fields[2:] {
			if strings.HasPrefix(f, "model:") {
				label = strings.TrimPrefix(f, "model:") + " · " + fields[0]
				break
			}
		}
		devices = append(devices, usbDevice{Serial: fields[0], Label: label})
	}
	if len(devices) == 0 {
		if unauthorized {
			updateNativeUSB("Izinkan USB debugging di layar HP.", nil)
		} else {
			updateNativeUSB("Menunggu HP. Jika tidak terdeteksi, coba USB tethering.", nil)
		}
		return
	}
	if selected == "" && len(devices) == 1 {
		selected = devices[0].Serial
		u.mu.Lock()
		u.selected = selected
		u.mu.Unlock()
	}
	valid := false
	for _, d := range devices {
		valid = valid || d.Serial == selected
	}
	if !valid {
		updateNativeUSB("Pilih HP yang akan dipakai.", devices)
		return
	}
	if attached != "" && attached != selected {
		_, _ = u.run("-s", attached, "reverse", "--remove", "tcp:8080")
	}
	listing, err := u.run("-s", selected, "reverse", "--list")
	if err != nil || !strings.Contains(listing, fmt.Sprintf("tcp:8080 tcp:%d", u.port)) {
		if _, err := u.run("-s", selected, "reverse", "--no-rebind", "tcp:8080", fmt.Sprintf("tcp:%d", u.port)); err != nil {
			updateNativeUSB("Port USB sibuk. Cabut-pasang kabel lalu coba lagi.", devices)
			return
		}
	}
	u.mu.Lock()
	u.attached = selected
	u.mu.Unlock()
	updateNativeUSB("USB siap. Buka Barcode Bridge di HP.", devices)
}

func extractADB() (string, error) {
	base, err := os.UserCacheDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(base, "VirtualBarcodeBridge", "adb-37.0.1")
	if err := os.MkdirAll(dir, 0700); err != nil {
		return "", err
	}
	for _, name := range []string{"adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "NOTICE.txt"} {
		data, err := adbFiles.ReadFile("usbtools/" + name)
		if err != nil {
			return "", err
		}
		path := filepath.Join(dir, name)
		if existing, err := os.ReadFile(path); err == nil && sha256.Sum256(existing) == sha256.Sum256(data) {
			continue
		}
		if err := os.WriteFile(path, data, 0700); err != nil {
			return "", err
		}
	}
	return filepath.Join(dir, "adb.exe"), nil
}

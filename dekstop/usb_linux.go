//go:build linux

package main

import (
	"context"
	"fmt"
	"os/exec"
	"strings"
	"sync"
	"time"
)

type usbDevice struct {
	Serial string `json:"serial"`
	Label  string `json:"label"`
}

type usbStatus struct {
	Available bool        `json:"available"`
	Active    bool        `json:"active"`
	State     string      `json:"state"`
	Message   string      `json:"message"`
	Selected  string      `json:"selected"`
	Devices   []usbDevice `json:"devices"`
}

type linuxUSB struct {
	mu       sync.Mutex
	status   usbStatus
	attached string
	port     int
	adb      string
	cancel   context.CancelFunc
	worker   sync.WaitGroup
}

func newUSBManager(port int) usbController {
	ctx, cancel := context.WithCancel(context.Background())
	path, err := exec.LookPath("adb")
	u := &linuxUSB{port: port, adb: path, cancel: cancel}
	u.status = usbStatus{Available: err == nil, State: "idle", Message: "Pilih USB untuk mulai mendeteksi HP.", Devices: []usbDevice{}}
	if err != nil {
		u.status.Message = "ADB belum terpasang. Pasang Android platform-tools (adb), lalu buka ulang ScanBridge."
	}
	u.worker.Add(1)
	go u.watch(ctx)
	return u
}

func (u *linuxUSB) Status() usbStatus {
	u.mu.Lock()
	defer u.mu.Unlock()
	status := u.status
	status.Devices = append([]usbDevice{}, u.status.Devices...)
	return status
}

func (u *linuxUSB) WebStatus() any { return u.Status() }

func (u *linuxUSB) SetActive(active bool) {
	u.mu.Lock()
	u.status.Active = active && u.status.Available
	if u.status.Active {
		u.status.State = "detecting"
		u.status.Message = "Mendeteksi HP melalui ADB…"
	} else {
		u.status.State = "idle"
		u.status.Message = "USB tidak aktif."
	}
	u.mu.Unlock()
}

func (u *linuxUSB) Select(serial string) {
	u.mu.Lock()
	u.status.Selected = serial
	u.mu.Unlock()
}

func (u *linuxUSB) SelectValid(serial string) bool {
	u.mu.Lock()
	defer u.mu.Unlock()
	for _, device := range u.status.Devices {
		if device.Serial == serial {
			u.status.Selected = serial
			return true
		}
	}
	return false
}

func (u *linuxUSB) Close() {
	u.cancel()
	u.worker.Wait()
	u.mu.Lock()
	attached := u.attached
	u.mu.Unlock()
	if attached != "" {
		_, _ = u.run("-s", attached, "reverse", "--remove", "tcp:8080")
	}
}

func (u *linuxUSB) run(args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, u.adb, args...).CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

func (u *linuxUSB) watch(ctx context.Context) {
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

func (u *linuxUSB) poll() {
	u.mu.Lock()
	active, selected, attached := u.status.Active, u.status.Selected, u.attached
	u.mu.Unlock()
	if !active {
		if attached != "" {
			_, _ = u.run("-s", attached, "reverse", "--remove", "tcp:8080")
			u.mu.Lock()
			u.attached = ""
			u.mu.Unlock()
		}
		return
	}
	out, err := u.run("devices", "-l")
	if err != nil {
		u.update("error", "ADB gagal mengakses HP. Periksa izin USB/udev, lalu coba lagi.", nil)
		return
	}
	devices, unauthorized := parseUSBDevices(out)
	if len(devices) == 0 {
		if unauthorized {
			u.update("authorize", "Izinkan USB debugging di layar HP.", devices)
		} else {
			u.update("waiting", "Menunggu HP dengan USB debugging aktif.", devices)
		}
		return
	}
	if selected == "" && len(devices) == 1 {
		selected = devices[0].Serial
		u.Select(selected)
	}
	found := false
	for _, device := range devices {
		found = found || device.Serial == selected
	}
	if !found {
		u.update("select", "Pilih HP yang akan dipakai.", devices)
		return
	}
	if attached != "" && attached != selected {
		_, _ = u.run("-s", attached, "reverse", "--remove", "tcp:8080")
	}
	want := fmt.Sprintf("tcp:8080 tcp:%d", u.port)
	listing, err := u.run("-s", selected, "reverse", "--list")
	if err != nil {
		u.update("error", "ADB reverse gagal. Cabut-pasang kabel lalu coba lagi.", devices)
		return
	}
	if !strings.Contains(listing, want) {
		// Replace stale mappings, including those left by a previous run.
		_, _ = u.run("-s", selected, "reverse", "--remove", "tcp:8080")
		if output, err := u.run("-s", selected, "reverse", "tcp:8080", fmt.Sprintf("tcp:%d", u.port)); err != nil {
			u.update("error", "Tidak bisa membuat jalur USB: "+output, devices)
			return
		}
	}
	u.mu.Lock()
	u.attached = selected
	u.status.State = "ready"
	u.status.Message = "USB siap. Buka ScanBridge di HP dan pilih USB."
	u.status.Devices = devices
	u.mu.Unlock()
}

func (u *linuxUSB) update(state, message string, devices []usbDevice) {
	u.mu.Lock()
	u.status.State = state
	u.status.Message = message
	u.status.Devices = devices
	u.mu.Unlock()
}

func parseUSBDevices(output string) ([]usbDevice, bool) {
	devices := []usbDevice{}
	unauthorized := false
	for _, line := range strings.Split(output, "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 || !strings.Contains(line, "usb:") {
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
		for _, field := range fields[2:] {
			if strings.HasPrefix(field, "model:") {
				label = strings.TrimPrefix(field, "model:") + " · " + fields[0]
				break
			}
		}
		devices = append(devices, usbDevice{Serial: fields[0], Label: label})
	}
	return devices, unauthorized
}

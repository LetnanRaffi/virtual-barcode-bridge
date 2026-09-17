package adbbridge

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

type State string

const (
	NoADB        State = "adb_missing"
	NoDevice     State = "no_device"
	Unauthorized State = "unauthorized"
	Connected    State = "connected"
	Disconnected State = "disconnected"
)

type Status struct {
	State  State
	Serial string
	Detail string
}

type Manager struct {
	ADB      string
	HostPort int
	OnStatus func(Status)
}

func FindADB() string {
	if exe, err := os.Executable(); err == nil {
		name := "adb"
		if filepath.Ext(exe) == ".exe" { name = "adb.exe" }
		candidate := filepath.Join(filepath.Dir(exe), name)
		if info, err := os.Stat(candidate); err == nil && !info.IsDir() { return candidate }
	}
	if path, err := exec.LookPath("adb"); err == nil { return path }
	if path, err := exec.LookPath("adb.exe"); err == nil { return path }
	return ""
}

func (m *Manager) Run(ctx context.Context) {
	if m.ADB == "" { m.ADB = FindADB() }
	if m.HostPort == 0 { m.HostPort = 8765 }
	if m.ADB == "" {
		m.emit(Status{State: NoADB, Detail: "ADB not found; put adb.exe beside this app"})
		return
	}
	_ = m.command(ctx, "start-server")
	last := Status{}
	for {
		status := m.probe(ctx)
		if status.State == Connected {
			port := fmt.Sprintf("tcp:%d", m.HostPort)
			if err := m.command(ctx, "-s", status.Serial, "reverse", port, port); err != nil {
				status = Status{State: Disconnected, Serial: status.Serial, Detail: err.Error()}
			} else {
				status.Detail = fmt.Sprintf("USB tunnel ready on port %d", m.HostPort)
			}
		}
		if status != last { m.emit(status); last = status }
		select {
		case <-ctx.Done():
			if last.Serial != "" { _ = m.command(context.Background(), "-s", last.Serial, "reverse", "--remove", fmt.Sprintf("tcp:%d", m.HostPort)) }
			return
		case <-time.After(time.Second):
		}
	}
}

func (m *Manager) probe(ctx context.Context) Status {
	cmd := exec.CommandContext(ctx, m.ADB, "devices")
	out, err := cmd.Output()
	if err != nil { return Status{State: Disconnected, Detail: err.Error()} }
	for _, line := range strings.Split(string(out), "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 || fields[0] == "List" { continue }
		switch fields[1] {
		case "device": return Status{State: Connected, Serial: fields[0]}
		case "unauthorized": return Status{State: Unauthorized, Serial: fields[0], Detail: "Authorize this PC on Android"}
		}
	}
	return Status{State: NoDevice, Detail: "Connect Android with USB debugging enabled"}
}

func (m *Manager) command(ctx context.Context, args ...string) error {
	cmd := exec.CommandContext(ctx, m.ADB, args...)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		if text := strings.TrimSpace(stderr.String()); text != "" { return fmt.Errorf("%s", text) }
		return err
	}
	return nil
}

func (m *Manager) emit(status Status) {
	if m.OnStatus != nil { m.OnStatus(status) }
}

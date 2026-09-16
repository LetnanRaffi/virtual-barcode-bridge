package network

import (
	"fmt"
	"net"
	"sort"
)

// Option identifies an active interface so users can match the phone's network.
type Option struct {
	Label string `json:"label"`
	URL   string `json:"url"`
}

func Options(port int, override string) []Option {
	var options []Option
	seen := map[string]bool{}
	add := func(name, ip string) {
		if seen[ip] {
			return
		}
		seen[ip] = true
		options = append(options, Option{
			Label: name + " — " + ip,
			URL:   fmt.Sprintf("ws://%s/ws", net.JoinHostPort(ip, fmt.Sprint(port))),
		})
	}
	if override != "" {
		add("Manual", override)
	}
	interfaces, _ := net.Interfaces()
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := iface.Addrs()
		for _, addr := range addrs {
			ip, _, err := net.ParseCIDR(addr.String())
			if err != nil || ip.To4() == nil || !ip.IsGlobalUnicast() {
				continue
			}
			add(iface.Name, ip.String())
		}
	}
	// Prefer the interface used by the default route, while keeping every
	// active adapter selectable. IP ranges do not identify Wi-Fi versus LAN.
	if override == "" {
		if ip, err := LocalIP(); err == nil {
			preferred := fmt.Sprintf("ws://%s/ws", net.JoinHostPort(ip, fmt.Sprint(port)))
			sort.SliceStable(options, func(i, j int) bool {
				return options[i].URL == preferred && options[j].URL != preferred
			})
		}
	}
	return options
}

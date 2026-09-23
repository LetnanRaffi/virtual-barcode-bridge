package network

import (
	"fmt"
	"net"
	"sort"
	"strings"
)

// Option is an IPv4 address that the phone can use to reach the bridge.
// Label deliberately describes a likely adapter type: Go's standard library
// does not expose a reliable USB-tethering flag on every operating system.
type Option struct {
	Label string `json:"label"`
	URL   string `json:"url"`
}

// Options returns every usable local IPv4 address. It is kept for callers that
// do not need discovery errors; new code should use Discover for logging.
func Options(port int, override string) []Option {
	options, _ := Discover(port, override)
	return options
}

// Discover enumerates active, non-loopback IPv4 interfaces. A server bound to
// :port is reachable through each returned address, including USB tethering's
// ordinary IP network interface.
func Discover(port int, override string) ([]Option, error) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return nil, err
	}
	options := optionsFromInterfaces(port, override, interfaces)

	// Prefer the default route but retain every usable adapter so a user can
	// select Wi-Fi, Ethernet, or a tethering interface explicitly.
	if override == "" {
		if ip, err := LocalIP(); err == nil {
			preferred := endpoint(ip, port)
			sort.SliceStable(options, func(i, j int) bool {
				return options[i].URL == preferred && options[j].URL != preferred
			})
		}
	}
	return options, nil
}

func optionsFromInterfaces(port int, override string, interfaces []net.Interface) []Option {
	var options []Option
	seen := map[string]bool{}
	add := func(label, ip string) {
		if seen[ip] {
			return
		}
		seen[ip] = true
		options = append(options, Option{
			Label: label + " — " + ip,
			URL:   endpoint(ip, port),
		})
	}
	if override != "" {
		add("Manual", override)
	}
	for _, iface := range interfaces {
		// FlagUp plus a globally-routable IPv4 address is the portable signal
		// for an interface that can receive the existing HTTP server traffic.
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			ip, _, err := net.ParseCIDR(addr.String())
			if err != nil || ip.To4() == nil || !ip.IsGlobalUnicast() {
				continue
			}
			add(interfaceLabel(iface.Name), ip.String())
		}
	}
	return options
}

func endpoint(ip string, port int) string {
	return fmt.Sprintf("ws://%s/ws", net.JoinHostPort(ip, fmt.Sprint(port)))
}

func interfaceLabel(name string) string {
	lower := strings.ToLower(name)
	switch {
	case containsAny(lower, "rndis", "usb", "tether", "android", "mobile"):
		return "Likely USB/Tethering · " + name
	case containsAny(lower, "wi-fi", "wifi", "wlan", "wireless", "wlp", "wlx"):
		return "Wi-Fi · " + name
	case containsAny(lower, "ethernet", "eth", "enp", "eno", "ens", "enx"):
		return "Ethernet · " + name
	default:
		return "Network · " + name
	}
}

func containsAny(value string, parts ...string) bool {
	for _, part := range parts {
		if strings.Contains(value, part) {
			return true
		}
	}
	return false
}

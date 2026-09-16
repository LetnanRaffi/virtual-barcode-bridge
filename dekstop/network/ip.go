package network

import (
	"net"
)

// LocalIP returns the active LAN IP by routing to a public address.
// It never dials; only inspects the routing table (via UDP connection).
func LocalIP() (string, error) {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err != nil {
		return "", err
	}
	defer conn.Close()

	local := conn.LocalAddr().(*net.UDPAddr)
	return local.IP.String(), nil
}

// AllNonLoopbackIPs lists every non-loopback IPv4 address on the machine.
func AllNonLoopbackIPs() []string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return nil
	}

	var out []string
	for _, a := range addrs {
		ipnet, ok := a.(*net.IPNet)
		if !ok {
			continue
		}
		ip := ipnet.IP.To4()
		if ip == nil || ip.IsLoopback() {
			continue
		}
		out = append(out, ip.String())
	}
	return out
}
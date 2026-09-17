package adbbridge

import "testing"

func TestStatesAreStable(t *testing.T) {
	for _, state := range []State{NoADB, NoDevice, Unauthorized, Connected, Disconnected} {
		if state == "" { t.Fatal("empty state") }
	}
}

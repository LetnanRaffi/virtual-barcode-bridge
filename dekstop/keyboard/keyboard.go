// Package keyboard defines the keystroke injection interface and OS factory.
package keyboard

// Injector types simulated keystrokes into the focused window.
type Injector interface {
	Type(text string) error
	Enter() error
}

// New returns the injector for the current OS.
func New() Injector {
	return newOSInjector()
}
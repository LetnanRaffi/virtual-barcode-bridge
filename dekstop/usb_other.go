//go:build !windows && !linux

package main

type unusedUSB struct{}

func newUSBManager(int) usbController { return unusedUSB{} }
func (unusedUSB) SetActive(bool)      {}
func (unusedUSB) Select(string)       {}
func (unusedUSB) Close()              {}

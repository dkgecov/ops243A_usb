package com.example.test_camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.hardware.usb.UsbManager

class UsbDeviceInitializer(
    private val context: Context,
    private val usbReceiver: BroadcastReceiver,
) {

    public fun registerReceivers(usbPermissionAction: String) {
        val usbPermissionFilter = IntentFilter(usbPermissionAction)
        val attachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)

        context.registerReceiver(usbReceiver, usbPermissionFilter, Context.RECEIVER_EXPORTED)
        context.registerReceiver(usbReceiver, attachFilter, Context.RECEIVER_EXPORTED)
        context.registerReceiver(usbReceiver, detachFilter, Context.RECEIVER_EXPORTED)
    }


}
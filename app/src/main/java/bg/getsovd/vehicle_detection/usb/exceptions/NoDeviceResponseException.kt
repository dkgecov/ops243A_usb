package bg.getsovd.vehicle_detection.usb.exceptions

import java.io.IOException


class NoDeviceResponseException(
    message: String = "No response received from device"
) : IOException(message)
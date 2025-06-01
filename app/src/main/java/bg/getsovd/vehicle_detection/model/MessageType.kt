package bg.getsovd.vehicle_detection.model

import android.graphics.Color

enum class MessageType(val color: Int) {
    INFO(Color.WHITE),
    SUCCESS(Color.GREEN),
    WARNING(Color.YELLOW),
    ERROR(Color.RED)
}
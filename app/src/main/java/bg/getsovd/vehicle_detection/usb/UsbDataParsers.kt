package bg.getsovd.vehicle_detection.usb

object UsbDataParsers {
    public  fun isLikelySpeedReport(text: String): Boolean {
        return try {
            val trimmed = text.trim()

            // Case 1: Pure number
            trimmed.toDoubleOrNull() != null ||

                    // Case 2: Unit, value
                    Regex("""^"(kmph|mph|mps)",-?\d+(\.\d+)?$""", RegexOption.IGNORE_CASE).matches(trimmed) ||

                    // Case 3: JSON with "unit" and "speed"
                    Regex("""\{\s*"speed"\s*:\s*".+?"(?:\s*,\s*"unit"\s*:\s*".+?")?\s*\}""").containsMatchIn(trimmed)
        } catch (e: Exception) {
            false
        }
    }
}
package bg.getsovd.vehicle_detection.utils

import android.os.Handler
import android.view.View
import android.widget.TextView
import bg.getsovd.vehicle_detection.model.MessageType

class MessageDisplayer(
    private val infoTextView: TextView,
    private val uiHandler: Handler
) {
    private var lastHideRunnable: Runnable? = null

    fun showMessage(message: String, type: MessageType, hideInterval: Long? = null) {
        uiHandler.post {
            infoTextView.apply {
                visibility = View.VISIBLE
                setTextColor(type.color)
                text = message
            }

            lastHideRunnable?.let { uiHandler.removeCallbacks(it) }

            if (hideInterval != null) {
                val hideRunnable = Runnable {
                    infoTextView.visibility = View.GONE
                }
                lastHideRunnable = hideRunnable
                uiHandler.postDelayed(hideRunnable, hideInterval)
            }
        }
    }
}
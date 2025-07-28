package bg.getsovd.vehicle_detection.utils

import com.google.firebase.auth.FirebaseAuth

object FirebaseAuthManager {
    fun signInAnonymously(onComplete: (Boolean, Exception?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception)
                }
            }
    }
}
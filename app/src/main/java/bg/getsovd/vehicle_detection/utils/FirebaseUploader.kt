package bg.getsovd.vehicle_detection.utils

import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storage


import java.io.File

object FirebaseUploader {

    private val TAG = "FirebaseUploader"

    /**
     * Upload a video file from a Uri to Firebase Storage
     * @param context: Android context (not strictly needed here, but handy for extensions)
     * @param videoUri: Uri pointing to the video file to upload
     * @param onComplete: callback with the download URL string or null if failed
     */
    fun uploadVideo(
        videoUri: Uri,
        onComplete: (downloadUrl: String?) -> Unit
    ) {

        val auth = FirebaseAuth.getInstance()

        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    // Signed in anonymously
                    Log.d("Auth", "Signed in anonymously: ${user?.uid}")
                    // Now you can upload videos securely
                } else {
                    // Sign-in failed
                    Log.e("Auth", "Anonymous sign-in failed", task.exception)
                }
            }

        val storage = Firebase.storage
        val storageRef: StorageReference = storage.reference

        // Use timestamp + random suffix to avoid collisions
        val fileName = "videos/${File(videoUri.path!!).name}"
        val videoRef = storageRef.child(fileName)

        val uploadTask = videoRef.putFile(videoUri)

        uploadTask
            .addOnSuccessListener {
                videoRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        Log.d(TAG, "Upload successful: $uri")
                        onComplete(uri.toString())
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get download URL", e)
                        onComplete(null)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Upload failed", e)
                onComplete(null)
            }
    }
}
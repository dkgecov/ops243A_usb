package com.example.test_camera.util

import android.content.Context
import com.example.test_camera.R
import java.io.File

object StorageUtils {

    fun getOutputDirectory(context: Context): File {
        val mediaDir = context.externalMediaDirs
            .firstOrNull()
            ?.let { externalDir ->
                File(externalDir, context.getString(R.string.app_name)).apply {
                    mkdirs()
                }
            }

        return if (mediaDir != null && mediaDir.exists()) {
            mediaDir
        } else {
            context.filesDir
        }
    }
}
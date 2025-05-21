package com.example.test_camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.example.test_camera.util.StorageUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService

class ImageProcessor (private val imageCapture: ImageCapture,private val imageProcessingExecutor: ExecutorService
,val context:Context) {

    private val outputDir = StorageUtils.getOutputDirectory(context)

    fun takePhoto(speed: Float) {
        val photoFile = File(outputDir, "IMG_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            imageProcessingExecutor , // ✅ Runs on a background thread
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        // Stamp the photo (heavy work is safe here)
                            val stampedFile = stampImageWithText(photoFile, speed.toString())
                        Log.d("Stamping", "Stamped image saved to: ${stampedFile.absolutePath}")

                        // Optional: show success on UI
                        // Handler(Looper.getMainLooper()).post {
                        //    Toast.makeText(this@YourActivityName, "Photo stamped!", Toast.LENGTH_SHORT).show()
                        // }

                    } catch (e: Exception) {
                        Log.e("Stamping", "Failed to stamp image: ${e.message}", e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }
    private fun getByteArrayFromBitmap(bitmap: Bitmap): ByteArray {

        // Rotate the bitmap by 90 degrees
        val matrix = Matrix()
        matrix.postRotate(90f)

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val width = rotatedBitmap.width
        val height = rotatedBitmap.height

        val pixels = IntArray(width * height) // Holds ARGB pixels
        val rgbByteArray = ByteArray(width * height * 3) // Holds RGB888 data

        rotatedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert ARGB to RGB888
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            rgbByteArray[i * 3] = r.toByte()
            rgbByteArray[i * 3 + 1] = g.toByte()
            rgbByteArray[i * 3 + 2] = b.toByte()
        }

        return rgbByteArray
    }
    // Process the captured image
     fun processImage(imageProxy: ImageProxy) {
        // Convert ImageProxy to Bitmap
        val bitmap = imageProxy.toBitmap()

        // Resize the Bitmap to Edge Impulse model size
        // val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        // resizing is done in C++ code

        // Convert the resized bitmap to ByteArray
        val byteArray = getByteArrayFromBitmap(bitmap)

        // Close the imageProxy after processing
        imageProxy.close()

        // Pass to C++ for Edge Impulse inference
      //  lifecycleScope.launch(Dispatchers.IO) {
        //    val result = passToCpp(byteArray)
          //  runOnUiThread {
          //      displayResults(result)
           // }
        //}
    }
    //private external fun passToCpp(imageData: ByteArray): InferenceResult?
    fun stampImageWithText(
        originalFile: File,
        speed: String
    ): File {
        // Load original image
        val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            textSize = 128f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(2f, 2f, 2f, Color.BLACK)
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val text = "Speed: $speed km/h\n$timestamp"

        // Draw text on bottom left
        canvas.drawText(text, 40f, mutableBitmap.height - 80f, paint)

        // Overwrite the original file or create a new one
        val stampedFile = File(originalFile.parent, "STAMPED_${originalFile.name}")
        FileOutputStream(stampedFile).use { out ->
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }

        return stampedFile
    }


}
package bg.getsovd.vehicle_detection.ui.options
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import bg.getsovd.vehicle_detection.R
import bg.getsovd.vehicle_detection.config.AppConfig
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity.Companion.OPTION_COSINE_ERROR
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity.Companion.OPTION_TYPE
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity.Companion.SELECTED_OBJECTS_ANGLE


private const val objectsToSensorAngle = "objects to sensor angle:"

class CosineError : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(OPTION_TYPE) == OPTION_COSINE_ERROR) {
            setupCosineErrorUI()
        } else {
            //TODO invalid option
        }
    }

    private fun setupCosineErrorUI() {
        setContentView(R.layout.dialog_cosine_error)
        var objectsAngle=intent.getFloatExtra(DEFAULT_OBJECTS_ANGLE, AppConfig.DEFAULT_OBJECTS_ANGLE) // get from app global object if not present in the intent
        val thresholdSeekBar = findViewById<SeekBar>(R.id.cosineErrorSeekBar)
        val thresholdLabel = findViewById<TextView>(R.id.cosineErrorLabel)
        val saveButton = findViewById<Button>(R.id.saveButton)
        saveButton.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra(SELECTED_OBJECTS_ANGLE, objectsAngle)
            resultIntent.putExtra(OPTION_TYPE, OPTION_COSINE_ERROR)
            setResult(RESULT_OK, resultIntent)
            val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            with (sharedPref.edit()) {
                putFloat("OBJECTS_ANGLE", objectsAngle)
                apply() // or commit()
            }
            finish()
        }
        thresholdSeekBar.progress = (objectsAngle).toInt()// set to the last known value
        thresholdLabel.text = "$objectsToSensorAngle$objectsAngle"

        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                objectsAngle = progress.toFloat()
                thresholdLabel.text = "$objectsToSensorAngle$objectsAngle"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val resultIntent = Intent()
                resultIntent.putExtra(SELECTED_OBJECTS_ANGLE, objectsAngle)
                resultIntent.putExtra(OPTION_TYPE, OPTION_COSINE_ERROR)
                setResult(RESULT_OK, resultIntent)// keep user choice even if save not clicked
            }
        })
    }
    companion object {
        const val COSINE_ERROR = "cosine_error"
        const val DEFAULT_OBJECTS_ANGLE = "default_cosine_angle"

    }
}
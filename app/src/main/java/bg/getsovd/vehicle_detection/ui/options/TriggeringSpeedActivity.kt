package bg.getsovd.vehicle_detection.ui.options

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import bg.getsovd.vehicle_detection.R
import bg.getsovd.vehicle_detection.config.AppConfig
import bg.getsovd.vehicle_detection.utils.AppConstants.DEFAULT_TRIGGER_SPEED
import bg.getsovd.vehicle_detection.utils.AppConstants.OPTION_TRIGGER_SPEED
import bg.getsovd.vehicle_detection.utils.AppConstants.OPTION_TYPE
import bg.getsovd.vehicle_detection.utils.AppConstants.SELECTED_TRIGGER_SPEED

private const val TRIGGER_SPEED = "TRIGGER_SPEED"
private const val SPEED = "speed:"
class TriggeringSpeedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(OPTION_TYPE)==OPTION_TRIGGER_SPEED) {
             setupThresholdUI()
        }else{
            //invalid option
        }
    }
    private fun setupThresholdUI() {
        setContentView(R.layout.dialog_threshold)
        var triggerSpeed=intent.getFloatExtra(DEFAULT_TRIGGER_SPEED, AppConfig.DEFAULT_TRIGGER_SPEED) // get from app global object if not present in the intent
        val thresholdSeekBar = findViewById<SeekBar>(R.id.thresholdSeekBar)
        val thresholdLabel = findViewById<TextView>(R.id.thresholdLabel)
        val saveButton = findViewById<Button>(R.id.saveButton)
        saveButton.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra(SELECTED_TRIGGER_SPEED, triggerSpeed)
            resultIntent.putExtra(OPTION_TYPE, OPTION_TRIGGER_SPEED)
            setResult(RESULT_OK, resultIntent)
            val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            with (sharedPref.edit()) {
                putFloat(TRIGGER_SPEED, triggerSpeed)
                apply() // or commit()
            }
            finish()
        }
        thresholdSeekBar.progress = (triggerSpeed).toInt()// set to the last known value
        thresholdLabel.text = "$SPEED $triggerSpeed"

        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                triggerSpeed = progress.toFloat()
                thresholdLabel.text = "$SPEED $triggerSpeed"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val resultIntent = Intent()
                resultIntent.putExtra(SELECTED_TRIGGER_SPEED, triggerSpeed)
                resultIntent.putExtra(OPTION_TYPE, OPTION_TRIGGER_SPEED)
                setResult(RESULT_OK, resultIntent)// keep user choice even if save not clicked
            }
        })
    }

    companion object {

    }
}
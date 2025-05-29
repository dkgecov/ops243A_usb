package bg.getsovd.vehicle_detection.ui.options

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import bg.getsovd.vehicle_detection.R

class TriggeringSpeedActivity : AppCompatActivity() {
    private val SPEED = "speed:"//TODO extract proper constant

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
        var triggerSpeed=intent.getFloatExtra(DEFAULT_TRIGGER_SPEED,0f)
        val thresholdSeekBar = findViewById<SeekBar>(R.id.thresholdSeekBar)
        val thresholdLabel = findViewById<TextView>(R.id.thresholdLabel)
        val saveButton = findViewById<Button>(R.id.saveButton)
        saveButton.setOnClickListener {
            // Here, you can save the triggerSpeed and finish the activity
            val resultIntent = Intent()
            resultIntent.putExtra(SELECTED_TRIGGER_SPEED, triggerSpeed)
            resultIntent.putExtra(OPTION_TYPE, OPTION_TRIGGER_SPEED)
            setResult(RESULT_OK, resultIntent)
            val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            with (sharedPref.edit()) {
                putFloat("TRIGGER_SPEED", triggerSpeed)//TODO const
                apply() // or commit()
            }
            finish()
        }
        thresholdSeekBar.progress = (triggerSpeed).toInt()
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
                setResult(RESULT_OK, resultIntent)
            }
        })
    }

    companion object {
        const val  RESULT_UNITS= "result_units"
        const val OPTION_TYPE = "option_type"
        const val OPTION_TRIGGER_SPEED = "trigger_speed"
        const val OPTION_UNITS = "units"
        const val DEFAULT_TRIGGER_SPEED = "default_trigger_speed"
        const val SELECTED_TRIGGER_SPEED = "selected_trigger_speed"
    }
}
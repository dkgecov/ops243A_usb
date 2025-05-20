package com.example.test_camera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OptionsActivity : AppCompatActivity() {
    private val SPEED = "speed:"
  //  private var triggerSpeed = 60.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(OPTION_TYPE)) {
            OPTION_TRIGGER_SPEED ->setupThresholdUI()
        //    OPTION_UNITS -> setContentView(R.layout.activity_units)
          //  OPTION_ABOUT -> setContentView(R.layout.activity_about)
           // else -> setContentView(R.layout.activity_options_default)
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
            resultIntent.putExtra(OPTION_TYPE,OPTION_TRIGGER_SPEED)
            setResult(RESULT_OK, resultIntent)
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
                resultIntent.putExtra(OPTION_TYPE,OPTION_TRIGGER_SPEED)
                setResult(RESULT_OK, resultIntent)
            }
        })
    }

    private fun setupUnitsUI() {
        // Set up units UI
    }

    private fun showAboutInfo() {
        // Display info
    }
    companion object {
        const val  RESULT_UNITS= "result_units"
        const val OPTION_TYPE = "option_type"
        const val OPTION_TRIGGER_SPEED = "trigger_speed"
        const val OPTION_UNITS = "units"
        const val OPTION_ABOUT = "about"
        const val DEFAULT_TRIGGER_SPEED = "default_trigger_speed"
        const val SELECTED_TRIGGER_SPEED = "selected_trigger_speed"
    }
}
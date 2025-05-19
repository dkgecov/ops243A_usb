package com.example.test_camera

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OptionsActivity : AppCompatActivity() {
    private val TRIGGER_SPEED = "Trigger speed:"
    private var triggerSpeed = 60.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
      /*  setContentView(R.layout.layout_options)

        val thresholdSeekBar = findViewById<SeekBar>(R.id.thresholdSeekBar)
        val thresholdLabel = findViewById<TextView>(R.id.thresholdLabel)

        thresholdSeekBar.progress = (triggerSpeed).toInt()
        thresholdLabel.text = "$TRIGGER_SPEED $triggerSpeed"

        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                triggerSpeed = progress.toFloat()
                thresholdLabel.text = "$TRIGGER_SPEED $triggerSpeed"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })*/
    }
}
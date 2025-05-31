package bg.getsovd.vehicle_detection.ui.options

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import bg.getsovd.vehicle_detection.R
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity.Companion.OPTION_TRIGGER_SPEED
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity.Companion.OPTION_TYPE
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity.Companion.OPTION_UNITS
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity.Companion.SELECTED_TRIGGER_SPEED

class SpeedUnitsActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(OPTION_TYPE)== OPTION_UNITS) {
            setupSpeedUnitsUI()
        }else{
            //TODO invalid option
        }
    }
    private fun setupSpeedUnitsUI(){
        setContentView(R.layout.activity_speed_units)
        val spinner: Spinner = findViewById(R.id.speedUnitsSpinner)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.speed_units_array,     // Make sure this exists!
            R.layout.spinner_item          // Your custom layout
        )
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = adapter

        val saveButton = findViewById<Button>(R.id.saveButton)
        saveButton.setOnClickListener {
            // Here, you can save the triggerSpeed and finish the activity
            val resultIntent = Intent()
            val selectedUnits=spinner.selectedItem.toString()
            resultIntent.putExtra(SELECTED_UNITS, selectedUnits)
            resultIntent.putExtra(OPTION_TYPE, OPTION_UNITS)
            setResult(RESULT_OK, resultIntent)



            finish()
        }





    }
    companion object {
        const val  SELECTED_UNITS= "selected_units"
        const val OPTION_UNITS = "units"
        const val  SPEED_UNITS= "speed_units"
    }
}
package rocks.ndot.widgets.ui

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import rocks.ndot.widgets.R
import rocks.ndot.widgets.databinding.ActivityConfigureSundialBinding
import rocks.ndot.widgets.widgets.SundialWidgetProvider

private data class City(val label: String, val lat: Double, val lng: Double)

private val CITIES = listOf(
    City("Dhaka", 23.81, 90.41),
    City("Sylhet", 24.89, 91.87),
    City("London", 51.51, -0.13),
    City("New York", 40.71, -74.01),
    City("Dubai", 25.20, 55.27),
    City("Singapore", 1.35, 103.82),
    City("Tokyo", 35.68, 139.65),
    City("Sydney", -33.87, 151.21),
)

class ConfigureSundialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigureSundialBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var chosenName: String? = null
    private var chosenLat: Double = 0.0
    private var chosenLng: Double = 0.0

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fetchDeviceLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Standard widget-configure boilerplate: assume cancelled unless we explicitly
        // finish with RESULT_OK below, so a stray back-press or crash doesn't leave a
        // broken placeholder widget on the home screen.
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        binding = ActivityConfigureSundialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CITIES.forEach { city ->
            val chip = Chip(this).apply {
                text = city.label
                isCheckable = true
                setOnClickListener { selectLocation(city.label, city.lat, city.lng) }
            }
            binding.cityChipGroup.addView(chip)
        }

        binding.useLocationButton.setOnClickListener { requestLocation() }
        binding.saveButton.setOnClickListener { save() }
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED) fetchDeviceLocation()
        else requestLocationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    @SuppressLint("MissingPermission") // guarded by the permission check in requestLocation()
    private fun fetchDeviceLocation() {
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    binding.cityChipGroup.clearCheck()
                    selectLocation(getString(R.string.configure_sundial_your_location), loc.latitude, loc.longitude)
                }
            }
    }

    private fun selectLocation(name: String, lat: Double, lng: Double) {
        chosenName = name
        chosenLat = lat
        chosenLng = lng
        binding.selectedLocationText.text = getString(
            R.string.configure_sundial_selected, name, lat, lng
        )
        binding.saveButton.isEnabled = true
    }

    private fun save() {
        val name = chosenName ?: return

        SundialWidgetProvider.prefs(this).edit(appWidgetId) { prefix ->
            putString("${prefix}name", name)
            putFloat("${prefix}lat", chosenLat.toFloat())
            putFloat("${prefix}lng", chosenLng.toFloat())
        }

        val mgr = AppWidgetManager.getInstance(this)
        SundialWidgetProvider.updateOne(this, mgr, appWidgetId)
        SundialWidgetProvider.scheduleRefresh(this, appWidgetId)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

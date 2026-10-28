// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// ---- Battery ---------------------------------------------------------------------------

data class BatteryInfo(
    val percent: Int?,
    val charging: Boolean,
    val plugged: String?,
    /** Only present when the device reports it. */
    val temperatureC: Float?,
    val voltageMv: Int?,
    val health: String?,
    val technology: String?,
    val powerSaveMode: Boolean,
)

class BatteryMonitor(private val context: Context) {

    fun updates(): Flow<BatteryInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(parse(intent))
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { trySend(parse(it)) }
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    private fun parse(intent: Intent): BatteryInfo {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> null
        }
        val tempRaw = if (intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) else Int.MIN_VALUE
        // Reported in tenths of a degree; 0 and absurd values mean "not reported".
        val temp = if (tempRaw != Int.MIN_VALUE && tempRaw in -400..1000 && tempRaw != 0) tempRaw / 10f else null
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it in 1..10_000 }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure reported"
            else -> null
        }
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }
        val power = ContextCompat.getSystemService(context, PowerManager::class.java)
        return BatteryInfo(percent, charging, plugged, temp, voltage, health, tech, power?.isPowerSaveMode == true)
    }
}

// ---- Motion and orientation sensors ----------------------------------------------------

data class OrientationReading(
    /** Magnetic heading of the top of the phone, degrees clockwise from magnetic north. */
    val azimuthDegrees: Double,
    val accuracy: Int,
)

data class GravityReading(val x: Double, val y: Double, val z: Double)

class DeviceSensors(context: Context) {

    private val sm: SensorManager? = ContextCompat.getSystemService(context, SensorManager::class.java)

    val hasCompass: Boolean
        get() = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null && sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)

    val hasAccelerometer: Boolean get() = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    val hasBarometer: Boolean get() = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    /** Heading from the fused rotation vector, falling back to accelerometer + magnetometer. */
    fun orientation(): Flow<OrientationReading> = callbackFlow {
        val manager = sm
        if (manager == null) { close(); return@callbackFlow }
        val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveG = false
        var haveM = false
        var magAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(matrix, e.values)
                        SensorManager.getOrientation(matrix, angles)
                        trySend(OrientationReading(normalize(Math.toDegrees(angles[0].toDouble())), magAccuracy))
                    }
                    Sensor.TYPE_ACCELEROMETER -> { System.arraycopy(e.values, 0, gravity, 0, 3); haveG = true }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(e.values, 0, geomagnetic, 0, 3); haveM = true
                        magAccuracy = e.accuracy
                    }
                }
                if (rotation == null && haveG && haveM && e.sensor.type != Sensor.TYPE_ROTATION_VECTOR) {
                    if (SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(matrix, angles)
                        trySend(OrientationReading(normalize(Math.toDegrees(angles[0].toDouble())), magAccuracy))
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magAccuracy = accuracy
            }
        }
        if (rotation != null) {
            manager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
            // Still listen to the magnetometer for its calibration status.
            mag?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        } else if (accel != null && mag != null) {
            manager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            manager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        } else {
            close()
        }
        awaitClose { manager.unregisterListener(listener) }
    }

    fun gravity(): Flow<GravityReading> = callbackFlow {
        val manager = sm
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) { close(); return@callbackFlow }
        val filtered = DoubleArray(3)
        var first = true
        val lowPass = sensor.type == Sensor.TYPE_ACCELEROMETER
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                for (i in 0..2) {
                    val v = e.values[i].toDouble()
                    filtered[i] = if (first || !lowPass) v else filtered[i] + 0.2 * (v - filtered[i])
                }
                first = false
                trySend(GravityReading(filtered[0], filtered[1], filtered[2]))
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }

    /** Air pressure in hPa from the barometer, when present. */
    fun pressure(): Flow<Float> = callbackFlow {
        val manager = sm
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (manager == null || sensor == null) { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { trySend(e.values[0]) }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }

    private fun normalize(d: Double): Double = ((d % 360.0) + 360.0) % 360.0
}

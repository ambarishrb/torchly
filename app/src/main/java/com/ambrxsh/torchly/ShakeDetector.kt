package com.ambrxsh.torchly

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
    context: Context,
    private var shakeThreshold: Float = 12f, // initial sensitivity threshold
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime = 0L

    // --- Start listening to accelerometer ---
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // --- Stop listening ---
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    // --- Update threshold dynamically ---
    fun updateThreshold(newThreshold: Float) {
        shakeThreshold = newThreshold
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            val currentTime = System.currentTimeMillis()

            if (acceleration > shakeThreshold && currentTime - lastShakeTime > 1000) {
                lastShakeTime = currentTime
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

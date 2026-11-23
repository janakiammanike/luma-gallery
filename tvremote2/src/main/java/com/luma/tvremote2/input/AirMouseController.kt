package com.luma.tvremote2.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns phone motion into the same relative pointer deltas the touchpad sends.
 *
 * The gyroscope is preferred because it can actually be calibrated: a still
 * phone's bias is measured and subtracted, so the pointer does not drift when
 * the phone is held steady. Bias is also tracked slowly while the phone is
 * still, which compensates for temperature drift.
 *
 * If the device has no gyroscope, the fused rotation vector from the legacy
 * client is used instead (a proven fallback). If there is no motion sensor at
 * all, the touchpad remains fully usable — callers check [isAvailable].
 */
class AirMouseController(
    context: Context,
    private val onMove: (dx: Int, dy: Int) -> Unit,
    private val onStatus: (message: String) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val gyro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val sensor: Sensor? = gyro ?: rotationSensor

    // ---- gyroscope calibration ------------------------------------------------
    private val bias = FloatArray(3)
    private val calibrationSum = FloatArray(3)
    private var calibrationSamples = 0
    private var calibrating = false
    private var stillSamples = 0

    // ---- rotation-vector fallback --------------------------------------------
    private val rotationVector = FloatArray(4)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val previous = FloatArray(3)
    private var hasPrevious = false
    private var lastRotation = -1

    private var active = false
    private var lastEventNs = 0L
    private var smoothedX = 0f
    private var smoothedY = 0f

    var sensitivity: Float = 1f
    var invertX: Boolean = false
    var invertY: Boolean = false

    fun isAvailable(): Boolean = sensor != null
    fun usesGyroscope(): Boolean = gyro != null
    fun isActive(): Boolean = active

    fun sensorName(): String = when {
        sensor?.type == Sensor.TYPE_GYROSCOPE -> "Gyroscope (calibrated)"
        sensor?.type == Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game rotation vector"
        sensor?.type == Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
        else -> "No motion sensor"
    }

    fun configure(sensitivity: Float, invertX: Boolean, invertY: Boolean) {
        this.sensitivity = sensitivity.coerceIn(0.25f, 3f)
        this.invertX = invertX
        this.invertY = invertY
    }

    /**
     * Hold the phone still for about one second while the average angular rate
     * is measured. That average becomes the bias removed from every later
     * sample. On the rotation-vector fallback this just forgets the last pose.
     */
    fun calibrate() {
        hasPrevious = false
        smoothedX = 0f
        smoothedY = 0f
        lastEventNs = 0L
        stillSamples = 0
        if (gyro == null) {
            onStatus("Air mouse recentred · ${sensorName()}")
            return
        }
        calibrationSum.fill(0f)
        calibrationSamples = 0
        calibrating = true
        onStatus("Keep the phone still — calibrating gyroscope…")
    }

    fun start() {
        val selected = sensor
        if (selected == null) {
            onStatus("This phone has no motion sensor. Use the touchpad.")
            return
        }
        if (active) return
        calibrate()
        val started = sensorManager.registerListener(this, selected, SensorManager.SENSOR_DELAY_GAME)
        if (!started) {
            onStatus("Could not start the motion sensor")
            return
        }
        active = true
        onStatus(if (gyro != null) {
            "Calibrate once, then move the phone to point"
        } else {
            "Air mouse ON · ${sensorName()}"
        })
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
        calibrating = false
        smoothedX = 0f
        smoothedY = 0f
        onStatus("Air mouse off · touchpad still active")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.sensor.type != sensor?.type) return
        val now = event.timestamp
        if (lastEventNs != 0L && now - lastEventNs < MIN_INTERVAL_NS) return
        val dt = if (lastEventNs == 0L) 0f else {
            ((now - lastEventNs) / 1_000_000_000f).coerceIn(0f, MAX_DT)
        }
        lastEventNs = now

        if (gyro != null && event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            handleGyro(event.values, dt)
        } else {
            handleRotation(event.values)
        }
    }

    private fun handleGyro(values: FloatArray, dt: Float) {
        if (values.size < 3 || dt <= 0f) return

        if (calibrating) {
            calibrationSum[0] += values[0]
            calibrationSum[1] += values[1]
            calibrationSum[2] += values[2]
            calibrationSamples++
            if (calibrationSamples >= CALIBRATION_SAMPLES) {
                bias[0] = calibrationSum[0] / calibrationSamples
                bias[1] = calibrationSum[1] / calibrationSamples
                bias[2] = calibrationSum[2] / calibrationSamples
                calibrating = false
                onStatus("Gyroscope calibrated · ready to point")
            }
            return
        }

        val x = values[0] - bias[0]
        val y = values[1] - bias[1]
        val z = values[2] - bias[2]

        // Slowly track residual bias only while the phone is still, so a real
        // motion never gets absorbed into the calibration.
        if (abs(x) < STILL_RAD && abs(y) < STILL_RAD && abs(z) < STILL_RAD) {
            stillSamples++
            if (stillSamples >= STILL_SAMPLES) {
                bias[0] += (values[0] - bias[0]) * BIAS_LEARN
                bias[1] += (values[1] - bias[1]) * BIAS_LEARN
                bias[2] += (values[2] - bias[2]) * BIAS_LEARN
            }
        } else {
            stillSamples = 0
        }

        // Map angular rate to screen axes. Roll around the phone's own axis is
        // deliberately not used: it should not drag the TV pointer.
        val (yawRate, pitchRate) = when (currentRotation()) {
            Surface.ROTATION_90 -> x to y
            Surface.ROTATION_180 -> -y to -x
            Surface.ROTATION_270 -> -x to -y
            else -> -y to x
        }
        val screenX = if (abs(yawRate) < GYRO_DEADZONE) 0f else yawRate
        val screenY = if (abs(pitchRate) < GYRO_DEADZONE) 0f else pitchRate

        smoothedX = smoothedX * (1f - SMOOTHING) + screenX * SMOOTHING
        smoothedY = smoothedY * (1f - SMOOTHING) + screenY * SMOOTHING

        val scale = PIXELS_PER_RADIAN * sensitivity * dt
        emit(smoothedX * scale, smoothedY * scale)
    }

    private fun handleRotation(values: FloatArray) {
        if (values.size < 3) return
        rotationVector[0] = values[0]
        rotationVector[1] = values[1]
        rotationVector[2] = values[2]
        rotationVector[3] = if (values.size > 3) values[3] else 0f

        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val rotation = currentRotation()
        if (!hasPrevious || rotation != lastRotation) {
            rememberPose(rotation)
            return
        }

        val yaw = shortestAngle(orientation[0] - previous[0])
        val pitch = (orientation[1] - previous[1]).coerceIn(-MAX_ANGLE, MAX_ANGLE)
        rememberPose(rotation)

        val (screenX, screenY) = when (rotation) {
            Surface.ROTATION_90 -> pitch to yaw
            Surface.ROTATION_180 -> -yaw to -pitch
            Surface.ROTATION_270 -> -pitch to -yaw
            else -> yaw to -pitch
        }

        smoothedX = smoothedX * (1f - SMOOTHING) + screenX * SMOOTHING
        smoothedY = smoothedY * (1f - SMOOTHING) + screenY * SMOOTHING
        if (abs(smoothedX) < DEADZONE && abs(smoothedY) < DEADZONE) return

        val scale = PIXELS_PER_RADIAN * sensitivity
        emit(smoothedX * scale, smoothedY * scale)
    }

    private fun emit(rawX: Float, rawY: Float) {
        var dx = rawX.roundToInt()
        var dy = rawY.roundToInt()
        if (invertX) dx = -dx
        if (invertY) dy = -dy
        dx = dx.coerceIn(-MAX_STEP, MAX_STEP)
        dy = dy.coerceIn(-MAX_STEP, MAX_STEP)
        if (dx != 0 || dy != 0) onMove(dx, dy)
    }

    private fun rememberPose(rotation: Int) {
        previous[0] = orientation[0]
        previous[1] = orientation[1]
        previous[2] = orientation[2]
        hasPrevious = true
        lastRotation = rotation
    }

    private fun currentRotation(): Int = try {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0
    } catch (_: Exception) {
        Surface.ROTATION_0
    }

    private fun shortestAngle(radians: Float): Float {
        var value = radians
        while (value > PI) value -= TWO_PI
        while (value < -PI) value += TWO_PI
        return value
    }

    private companion object {
        const val MIN_INTERVAL_NS = 12_000_000L // ~80 Hz upper bound
        const val MAX_DT = 0.05f // ignore long gaps (screen off / background)
        const val SMOOTHING = 0.35f
        const val DEADZONE = 0.0018f // rotation path: per-sample radians
        const val GYRO_DEADZONE = 0.015f // rad/s, removes hand tremor
        const val STILL_RAD = 0.03f // rad/s, considered stationary
        const val STILL_SAMPLES = 20
        const val BIAS_LEARN = 0.01f
        const val CALIBRATION_SAMPLES = 60
        const val PIXELS_PER_RADIAN = 420f
        const val MAX_STEP = 48
        const val MAX_ANGLE = 0.25f
        const val PI = 3.14159265f
        const val TWO_PI = 6.2831853f
    }
}

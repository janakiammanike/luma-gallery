package com.luma.tvremote2.input

import android.content.Context
import android.hardware.Sensor
import android.os.SystemClock
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Which guided calibration step the sensor is currently measuring. */
enum class SensorCalibration { NONE, STILL, DIRECTION }

/**
 * Turns phone motion into the same relative pointer deltas the touchpad sends.
 *
 * Drift is handled in three layers:
 *  1. A guided still capture measures the gyroscope bias from a stable window.
 *  2. A variance-based zero-velocity check detects a stationary phone and
 *     suppresses output while it learns any remaining bias (hand tremor and
 *     temperature drift no longer move the TV cursor).
 *  3. A four-direction check derives the per-axis sign, and a four-corner TV
 *     scan (driven by the UI) supplies the gain so one sweep covers the screen.
 *
 * If the phone has no gyroscope, the fused rotation vector from the legacy
 * client is used as a fallback. With no motion sensor at all the touchpad stays
 * fully usable — callers check [isAvailable].
 */
class AirMouseController(
    context: Context,
    private val onMove: (dx: Int, dy: Int) -> Unit,
    private val onStatus: (message: String) -> Unit,
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    private val onDirection: (String, Float, Float) -> Unit = { _, _, _ -> },
    private val onShake: () -> Unit = {}
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val gyro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val sensor: Sensor? = gyro ?: rotationSensor

    // ---- bias and stillness tracking -----------------------------------------
    private val bias = FloatArray(3)
    private val historyX = FloatArray(STILL_WINDOW)
    private val historyY = FloatArray(STILL_WINDOW)
    private val historyZ = FloatArray(STILL_WINDOW)
    private var historyIndex = 0
    private var historyCount = 0

    // ---- calibration ----------------------------------------------------------
    private var calibration = SensorCalibration.NONE
    private var directionName = ""
    private val calibrationSum = FloatArray(3)
    private var calibrationSamples = 0
    private var strictStillCheck = true
    private var directionSumX = 0f
    private var directionSumY = 0f
    private var directionSamples = 0

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

    // ---- shake / recenter -----------------------------------------------------
    private var lastShakeSign = 0
    private var lastFlipNs = 0L
    private var shakeFlips = 0
    private var shakeCooldownNs = 0L
    private var suppressUntilNs = 0L

    var sensitivity: Float = 1f
    var invertX: Boolean = false
    var invertY: Boolean = false
    var gainX: Float = 1f
    var gainY: Float = 1f
    var axisSignX: Int = 1
    var axisSignY: Int = 1

    fun isAvailable(): Boolean = sensor != null
    fun usesGyroscope(): Boolean = gyro != null
    fun isActive(): Boolean = active
    fun calibrationPhase(): SensorCalibration = calibration

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

    fun applyGain(x: Float, y: Float) {
        gainX = x.coerceIn(0.25f, 4f)
        gainY = y.coerceIn(0.25f, 4f)
    }

    fun applyAxisSign(x: Int, y: Int) {
        axisSignX = if (x < 0) -1 else 1
        axisSignY = if (y < 0) -1 else 1
    }

    /** Suppresses pointer output briefly while a recenter move is sent. */
    fun pausePointer(ms: Long) {
        suppressUntilNs = SystemClock.elapsedRealtimeNanos() + ms * 1_000_000L
    }

    /**
     * Measures bias from a stable window; the wizard shows live progress.
     *
     * [strict] is used by the guided wizard: moving during the measurement
     * restarts it. Normal start-up uses a non-strict capture so the pointer
     * always becomes usable even if the phone is already in motion.
     */
    fun beginStillCalibration(strict: Boolean = true) {
        resetHistory()
        calibrationSum.fill(0f)
        calibrationSamples = 0
        strictStillCheck = strict
        calibration = SensorCalibration.STILL
        onProgress(0f, "Hold the phone still — measuring gyroscope bias")
    }

    /** Measures the average motion while the user tilts in one direction. */
    fun beginDirectionCalibration(direction: String) {
        directionSumX = 0f
        directionSumY = 0f
        directionSamples = 0
        directionName = direction
        calibration = SensorCalibration.DIRECTION
        onProgress(0f, "Tilt the phone $direction and hold it there")
    }

    fun cancelCalibration() {
        calibration = SensorCalibration.NONE
    }

    /** Registers the sensor. Guided calibration is started separately. */
    fun start() {
        val selected = sensor
        if (selected == null) {
            onStatus("This phone has no motion sensor. Use the touchpad.")
            return
        }
        if (active) return
        val started = sensorManager.registerListener(this, selected, SensorManager.SENSOR_DELAY_GAME)
        if (!started) {
            onStatus("Could not start the motion sensor")
            return
        }
        active = true
        resetHistory()
        onStatus("Air mouse ON · ${sensorName()}")
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
        calibration = SensorCalibration.NONE
        smoothedX = 0f
        smoothedY = 0f
        resetHistory()
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
            handleGyro(event.values, dt, now)
        } else {
            handleRotation(event.values)
        }
    }

    private fun handleGyro(values: FloatArray, dt: Float, now: Long) {
        if (values.size < 3 || dt <= 0f) return
        val realNow = SystemClock.elapsedRealtimeNanos()
        recordHistory(values)

        if (calibration == SensorCalibration.STILL) {
            if (strictStillCheck && historyCount == STILL_WINDOW &&
                historyVariance() > UNSTABLE_VARIANCE
            ) {
                calibrationSum.fill(0f)
                calibrationSamples = 0
                resetHistory()
                onProgress(0f, "You moved — hold the phone still again")
                return
            }
            calibrationSum[0] += values[0]
            calibrationSum[1] += values[1]
            calibrationSum[2] += values[2]
            calibrationSamples++
            val progress = (calibrationSamples.toFloat() / CALIBRATION_SAMPLES).coerceAtMost(1f)
            if (calibrationSamples % 8 == 0 || progress == 1f) {
                onProgress(progress, "Hold the phone still — ${(progress * 100).toInt()}%")
            }
            if (calibrationSamples >= CALIBRATION_SAMPLES) {
                bias[0] = calibrationSum[0] / calibrationSamples
                bias[1] = calibrationSum[1] / calibrationSamples
                bias[2] = calibrationSum[2] / calibrationSamples
                calibration = SensorCalibration.NONE
                smoothedX = 0f
                smoothedY = 0f
                resetHistory()
                onProgress(1f, "Gyroscope stable · bias captured")
            }
            return
        }

        val rx = values[0] - bias[0]
        val ry = values[1] - bias[1]
        val rz = values[2] - bias[2]
        val (screenX, screenY) = mappedRates(rx, ry, rz)

        if (calibration == SensorCalibration.DIRECTION) {
            directionSumX += screenX
            directionSumY += screenY
            directionSamples++
            val progress = (directionSamples.toFloat() / DIRECTION_SAMPLES).coerceAtMost(1f)
            if (directionSamples % 8 == 0 || progress == 1f) {
                onProgress(progress, "Tilt $directionName — ${(progress * 100).toInt()}%")
            }
            if (directionSamples >= DIRECTION_SAMPLES) {
                val avgX = directionSumX / directionSamples
                val avgY = directionSumY / directionSamples
                calibration = SensorCalibration.NONE
                onDirection(directionName, avgX, avgY)
            }
            return
        }

        if (realNow < suppressUntilNs) return

        detectShake(screenX, now)
        if (realNow < suppressUntilNs) return

        // Zero-velocity update: when the phone is stationary, keep the bias
        // learning and force the pointer to rest. This is what stops drift.
        if (isStill()) {
            val meanX = windowMean(historyX)
            val meanY = windowMean(historyY)
            val meanZ = windowMean(historyZ)
            bias[0] += (meanX - bias[0]) * BIAS_LEARN
            bias[1] += (meanY - bias[1]) * BIAS_LEARN
            bias[2] += (meanZ - bias[2]) * BIAS_LEARN
            smoothedX = 0f
            smoothedY = 0f
            return
        }

        val cleanX = applyDeadzone(screenX)
        val cleanY = applyDeadzone(screenY)
        smoothedX = smoothedX * (1f - SMOOTHING) + cleanX * SMOOTHING
        smoothedY = smoothedY * (1f - SMOOTHING) + cleanY * SMOOTHING

        val scale = PIXELS_PER_RADIAN * sensitivity * gainX * dt
        val scaleY = PIXELS_PER_RADIAN * sensitivity * gainY * dt
        emit(smoothedX * scale, smoothedY * scaleY)
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

        var sx: Float
        var sy: Float
        when (rotation) {
            Surface.ROTATION_90 -> { sx = pitch; sy = yaw }
            Surface.ROTATION_180 -> { sx = -yaw; sy = -pitch }
            Surface.ROTATION_270 -> { sx = -pitch; sy = -yaw }
            else -> { sx = yaw; sy = -pitch }
        }
        sx *= axisSignX
        sy *= axisSignY
        if (invertX) sx = -sx
        if (invertY) sy = -sy

        smoothedX = smoothedX * (1f - SMOOTHING) + sx * SMOOTHING
        smoothedY = smoothedY * (1f - SMOOTHING) + sy * SMOOTHING
        if (abs(smoothedX) < DEADZONE && abs(smoothedY) < DEADZONE) return

        emit(
            smoothedX * PIXELS_PER_RADIAN * sensitivity * gainX,
            smoothedY * PIXELS_PER_RADIAN * sensitivity * gainY
        )
    }

    /** Maps angular rate to screen axes; roll is deliberately ignored. */
    private fun mappedRates(x: Float, y: Float, z: Float): Pair<Float, Float> {
        var sx: Float
        var sy: Float
        when (currentRotation()) {
            Surface.ROTATION_90 -> { sx = x; sy = y }
            Surface.ROTATION_180 -> { sx = -y; sy = -x }
            Surface.ROTATION_270 -> { sx = -x; sy = -y }
            else -> { sx = -y; sy = x }
        }
        sx *= axisSignX
        sy *= axisSignY
        if (invertX) sx = -sx
        if (invertY) sy = -sy
        return sx to sy
    }

    private fun emit(rawX: Float, rawY: Float) {
        var dx = (rawX).roundToInt()
        var dy = (rawY).roundToInt()
        dx = dx.coerceIn(-MAX_STEP, MAX_STEP)
        dy = dy.coerceIn(-MAX_STEP, MAX_STEP)
        if (dx != 0 || dy != 0) onMove(dx, dy)
    }

    // ---- stillness -------------------------------------------------------------

    private fun recordHistory(values: FloatArray) {
        historyX[historyIndex] = values[0]
        historyY[historyIndex] = values[1]
        historyZ[historyIndex] = values[2]
        historyIndex = (historyIndex + 1) % STILL_WINDOW
        if (historyCount < STILL_WINDOW) historyCount++
    }

    private fun resetHistory() {
        historyIndex = 0
        historyCount = 0
    }

    private fun windowMean(buffer: FloatArray): Float {
        var sum = 0f
        for (value in buffer) sum += value
        return sum / STILL_WINDOW
    }

    private fun historyVariance(): Float {
        val mx = windowMean(historyX)
        val my = windowMean(historyY)
        val mz = windowMean(historyZ)
        var sum = 0f
        for (i in 0 until STILL_WINDOW) {
            val dx = historyX[i] - mx
            val dy = historyY[i] - my
            val dz = historyZ[i] - mz
            sum += dx * dx + dy * dy + dz * dz
        }
        return sum / STILL_WINDOW / 3f
    }

    private fun isStill(): Boolean {
        if (historyCount < STILL_WINDOW) return false
        val mx = windowMean(historyX)
        val my = windowMean(historyY)
        val mz = windowMean(historyZ)
        return historyVariance() < STILL_VARIANCE &&
            sqrt(mx * mx + my * my + mz * mz) < STILL_MAGNITUDE
    }

    private fun applyDeadzone(value: Float): Float {
        if (abs(value) <= GYRO_DEADZONE) return 0f
        return value - Math.copySign(GYRO_DEADZONE.toDouble(), value.toDouble()).toFloat()
    }

    // ---- shake to center -------------------------------------------------------

    private fun detectShake(screenX: Float, now: Long) {
        if (abs(screenX) < SHAKE_MIN_RATE || now < shakeCooldownNs) return
        val sign = if (screenX > 0f) 1 else -1
        if (lastShakeSign == 0) {
            lastShakeSign = sign
            lastFlipNs = now
            shakeFlips = 1
            return
        }
        if (sign == lastShakeSign) return
        if (now - lastFlipNs <= SHAKE_WINDOW_NS) {
            shakeFlips++
            if (shakeFlips >= SHAKE_FLIPS) {
                shakeCooldownNs = now + SHAKE_COOLDOWN_NS
                suppressUntilNs = SystemClock.elapsedRealtimeNanos() + SHAKE_SUPPRESS_NS
                shakeFlips = 0
                lastShakeSign = 0
                onShake()
                return
            }
        } else {
            shakeFlips = 1
        }
        lastShakeSign = sign
        lastFlipNs = now
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
        const val MAX_DT = 0.05f
        const val SMOOTHING = 0.35f
        const val DEADZONE = 0.0018f // rotation fallback, per sample
        const val GYRO_DEADZONE = 0.02f // rad/s, tremor floor
        const val STILL_WINDOW = 20
        const val STILL_VARIANCE = 0.00012f // (rad/s)^2
        const val STILL_MAGNITUDE = 0.08f // rad/s
        const val UNSTABLE_VARIANCE = 0.0006f
        const val BIAS_LEARN = 0.02f
        const val CALIBRATION_SAMPLES = 160 // ~2 s at 80 Hz
        const val DIRECTION_SAMPLES = 60 // ~0.75 s
        const val PIXELS_PER_RADIAN = 420f
        const val MAX_STEP = 48
        const val MAX_ANGLE = 0.25f
        const val SHAKE_MIN_RATE = 0.25f
        const val SHAKE_WINDOW_NS = 800_000_000L
        const val SHAKE_FLIPS = 3
        const val SHAKE_COOLDOWN_NS = 3_000_000_000L
        const val SHAKE_SUPPRESS_NS = 500_000_000L
        const val PI = 3.14159265f
        const val TWO_PI = 6.2831853f
    }
}

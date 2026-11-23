package com.luma.tvremote.input

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
 * Turns phone tilt into the same relative pointer deltas the touchpad sends.
 *
 * The rotation vector sensor already expresses orientation in a world frame,
 * so this controller compares each sample with the previous one. Pointer speed
 * therefore follows how fast the phone is turning; holding it still stops the
 * cursor instead of pinning it to a corner. Roll is deliberately not used:
 * rotating the phone in its own plane should not drag the TV pointer. The delta
 * is low-pass filtered and mapped through the current Android display rotation
 * before it is handed to WebOsClient.move(dx, dy).
 */
class AirMouseController(
    context: Context,
    private val onMove: (dx: Int, dy: Int) -> Unit,
    private val onStatus: (message: String) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationVector = FloatArray(4)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val previous = FloatArray(3)

    private var hasPrevious = false
    private var active = false
    private var lastEventNs = 0L
    private var lastRotation = -1
    private var smoothedX = 0f
    private var smoothedY = 0f

    var sensitivity: Float = 1f
    var invertX: Boolean = false
    var invertY: Boolean = false

    fun isAvailable(): Boolean = sensor != null

    fun sensorName(): String = when (sensor?.type) {
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game rotation vector"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
        else -> "No motion sensor"
    }

    fun isActive(): Boolean = active

    fun configure(sensitivity: Float, invertX: Boolean, invertY: Boolean) {
        this.sensitivity = sensitivity.coerceIn(0.25f, 3f)
        this.invertX = invertX
        this.invertY = invertY
    }

    /** Forget the previous sample so no leftover motion is applied. */
    fun calibrate() {
        hasPrevious = false
        smoothedX = 0f
        smoothedY = 0f
        lastEventNs = 0L
        if (active) onStatus("Air mouse recentred · ${sensorName()}")
    }

    fun start() {
        val selected = sensor
        if (selected == null) {
            onStatus("This phone has no rotation sensor for air mouse")
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
        onStatus("Air mouse ON · ${sensorName()}")
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
        smoothedX = 0f
        smoothedY = 0f
        onStatus("Air mouse off · touchpad still active")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.sensor.type != sensor?.type) return
        if (event.timestamp - lastEventNs < MIN_INTERVAL_NS) return
        lastEventNs = event.timestamp

        val values = event.values
        if (values.size < 3) return
        rotationVector[0] = values[0]
        rotationVector[1] = values[1]
        rotationVector[2] = values[2]
        // A game rotation vector may omit w; getRotationMatrixFromVector then
        // derives it from the unit quaternion. Passing 0 is treated as absent.
        rotationVector[3] = if (values.size > 3) values[3] else 0f

        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val rotation = currentRotation()
        if (!hasPrevious || rotation != lastRotation) {
            rememberPose(rotation)
            return
        }

        // getOrientation returns azimuth(yaw), pitch and roll in radians. Only
        // the first two are used, so phone roll is ignored by construction.
        // Use the CHANGE since the previous sample, not the angle from a fixed
        // reference: tilting 5° and holding still must move the pointer a little,
        // then stop. Otherwise the cursor pins itself to a screen corner.
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
        var dx = (smoothedX * scale).roundToInt()
        var dy = (smoothedY * scale).roundToInt()
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
        const val SMOOTHING = 0.35f
        const val DEADZONE = 0.0018f // per-sample radians; removes hand tremor
        const val PIXELS_PER_RADIAN = 420f // ~7 px per degree of rotation at 1x
        const val MAX_STEP = 48
        const val MAX_ANGLE = 0.25f // per-sample clamp; ignores sudden flips
        const val PI = 3.14159265f
        const val TWO_PI = 6.2831853f
    }
}

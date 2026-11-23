package com.luma.tvremote2.core

/** Stages of the guided air-mouse calibration wizard. */
enum class CalibrationStage { STILL, DIRECTIONS, CORNERS, DONE }

data class CalibrationUiState(
    val stage: CalibrationStage = CalibrationStage.STILL,
    val progress: Float = 0f,
    val message: String = "Hold the phone comfortably, then start the stability check.",
    val directions: List<String> = listOf("UP", "DOWN", "LEFT", "RIGHT"),
    val directionIndex: Int = 0,
    val directionResults: Map<String, String> = emptyMap(),
    val corners: List<String> = listOf("Top-left", "Top-right", "Bottom-right", "Bottom-left"),
    val cornerIndex: Int = 0,
    val accumDx: Int = 0,
    val accumDy: Int = 0,
    val spanX: Int = 0,
    val spanY: Int = 0,
    val gainX: Float = 1f,
    val gainY: Float = 1f,
    val axisSignX: Int = 1,
    val axisSignY: Int = 1,
    val shakeToCenter: Boolean = false
) {
    /** True only between corner marks, so pointer travel can be measured. */
    val cornerScanActive: Boolean
        get() = stage == CalibrationStage.CORNERS && cornerIndex > 0
}

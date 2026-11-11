package com.luma.gallery.ui.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.gallery.R
import com.luma.gallery.data.CropWindow
import com.luma.gallery.data.EditorFilter
import com.luma.gallery.data.ImageEditor
import com.luma.gallery.data.ImageEdits
import com.luma.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private enum class EditorTool { NONE, CROP, ROTATE, FILTERS, ADJUST }

private enum class CropRatio(val value: Float?) {
    FREE(null),
    SQUARE(1f),
    PORTRAIT(4f / 5f),
    LANDSCAPE(16f / 9f)
}

private enum class CropHandle { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Composable
fun ImageEditorScreen(
    item: MediaItem,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var base by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    var tool by remember { mutableStateOf(EditorTool.NONE) }
    var turns by remember { mutableIntStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(EditorFilter.ORIGINAL) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var ratio by remember { mutableStateOf(CropRatio.FREE) }

    BackHandler(enabled = !saving, onBack = onClose)

    LaunchedEffect(item.uri) {
        loading = true
        val loaded = ImageEditor.loadPreview(context, item.uri)
        base = loaded
        preview = loaded
        loading = false
    }

    LaunchedEffect(turns, flipH, flipV, base) {
        val source = base ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            ImageEditor.applyTransform(source, turns, flipH, flipV)
        }
    }

    val edits = ImageEdits(
        quarterTurns = turns,
        flipH = flipH,
        flipV = flipV,
        filter = filter,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        crop = CropWindow(crop.left, crop.top, crop.right, crop.bottom)
    )
    val colorFilter = remember(edits) {
        ColorFilter.colorMatrix(ColorMatrix(ImageEditor.colorMatrix(edits).array))
    }

    fun save() {
        if (saving || loading) return
        saving = true
        scope.launch {
            val displayName = "Luma_${System.currentTimeMillis()}.jpg"
            val saved = ImageEditor.saveCopy(context, item.uri, edits, displayName)
            saving = false
            if (saved != null) {
                Toast.makeText(context, R.string.editor_saved, Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, R.string.editor_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, enabled = !saving) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.image_editor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { save() }, enabled = !saving && !loading) {
                    Text(
                        text = stringResource(R.string.action_save),
                        color = if (saving || loading) Color(0x66FFFFFF)
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val image = preview
                if (image != null) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val containerWidth = with(density) { maxWidth.toPx() }
                        val containerHeight = with(density) { maxHeight.toPx() }
                        val imageRect = remember(image, containerWidth, containerHeight) {
                            fitRect(image.width, image.height, containerWidth, containerHeight)
                        }

                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (tool == EditorTool.CROP) {
                            CropOverlay(
                                imageRect = imageRect,
                                imageWidth = image.width,
                                imageHeight = image.height,
                                crop = crop,
                                ratio = ratio.value,
                                onCropChange = { crop = it }
                            )
                        }
                    }
                }

                if (loading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.editor_loading),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xB3FFFFFF)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121218))
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding()
                    )
            ) {
                when (tool) {
                    EditorTool.CROP -> CropPanel(
                        ratio = ratio,
                        onRatioChange = { next ->
                            ratio = next
                            crop = preview?.let {
                                fitCrop(it.width, it.height, next.value)
                            } ?: Rect(0f, 0f, 1f, 1f)
                        }
                    )

                    EditorTool.ROTATE -> RotatePanel(
                        onRotateLeft = {
                            turns = (turns + 3).mod(4)
                            crop = resetCrop(preview, ratio.value)
                        },
                        onRotateRight = {
                            turns = (turns + 1).mod(4)
                            crop = resetCrop(preview, ratio.value)
                        },
                        onFlip = {
                            flipH = !flipH
                            crop = resetCrop(preview, ratio.value)
                        }
                    )

                    EditorTool.FILTERS -> FiltersPanel(
                        bitmap = preview,
                        filter = filter,
                        edits = edits,
                        onFilterChange = { filter = it }
                    )

                    EditorTool.ADJUST -> AdjustPanel(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        onBrightness = { brightness = it },
                        onContrast = { contrast = it },
                        onSaturation = { saturation = it },
                        onReset = {
                            brightness = 0f
                            contrast = 0f
                            saturation = 0f
                        }
                    )

                    EditorTool.NONE -> Spacer(Modifier.height(16.dp))
                }

                ToolBar(tool = tool, onToolChange = { tool = it })
            }
        }

        if (saving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.editor_saving),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun resetCrop(bitmap: Bitmap?, ratio: Float?): Rect {
    return bitmap?.let { fitCrop(it.width, it.height, ratio) } ?: Rect(0f, 0f, 1f, 1f)
}

private fun fitCrop(imageWidth: Int, imageHeight: Int, ratio: Float?): Rect {
    if (ratio == null) return Rect(0f, 0f, 1f, 1f)
    if (imageWidth <= 0 || imageHeight <= 0) return Rect(0f, 0f, 1f, 1f)

    val normalisedRatio = ratio * imageHeight.toFloat() / imageWidth.toFloat()
    var width = 0.92f
    var height = width / normalisedRatio
    if (height > 0.92f) {
        height = 0.92f
        width = height * normalisedRatio
    }
    val left = (1f - width) / 2f
    val top = (1f - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun fitRect(imageWidth: Int, imageHeight: Int, width: Float, height: Float): Rect {
    if (imageWidth <= 0 || imageHeight <= 0 || width <= 0f || height <= 0f) return Rect.Zero
    val scale = min(width / imageWidth, height / imageHeight)
    val drawnWidth = imageWidth * scale
    val drawnHeight = imageHeight * scale
    val left = (width - drawnWidth) / 2f
    val top = (height - drawnHeight) / 2f
    return Rect(left, top, left + drawnWidth, top + drawnHeight)
}

@Composable
private fun CropOverlay(
    imageRect: Rect,
    imageWidth: Int,
    imageHeight: Int,
    crop: Rect,
    ratio: Float?,
    onCropChange: (Rect) -> Unit
) {
    val currentCrop by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(ratio)
    var handle by remember { mutableStateOf(CropHandle.NONE) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageRect, imageWidth, imageHeight) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val rect = cropInPixels(currentCrop, imageRect)
                        handle = hitHandle(offset, rect, 30.dp.toPx())
                    },
                    onDragEnd = { handle = CropHandle.NONE },
                    onDragCancel = { handle = CropHandle.NONE },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (handle == CropHandle.NONE) return@detectDragGestures
                        val dx = dragAmount.x / imageRect.width
                        val dy = dragAmount.y / imageRect.height
                        if (dx == 0f && dy == 0f) return@detectDragGestures

                        val next = when (handle) {
                            CropHandle.MOVE -> moveCrop(currentCrop, dx, dy)
                            CropHandle.TOP_LEFT -> resizeCrop(
                                currentCrop, dx, dy, currentRatio, imageWidth, imageHeight,
                                leftSide = true, topSide = true
                            )
                            CropHandle.TOP_RIGHT -> resizeCrop(
                                currentCrop, dx, dy, currentRatio, imageWidth, imageHeight,
                                leftSide = false, topSide = true
                            )
                            CropHandle.BOTTOM_LEFT -> resizeCrop(
                                currentCrop, dx, dy, currentRatio, imageWidth, imageHeight,
                                leftSide = true, topSide = false
                            )
                            CropHandle.BOTTOM_RIGHT -> resizeCrop(
                                currentCrop, dx, dy, currentRatio, imageWidth, imageHeight,
                                leftSide = false, topSide = false
                            )
                            CropHandle.NONE -> currentCrop
                        }
                        onCropChange(next)
                    }
                )
            }
    ) {
        val rect = cropInPixels(currentCrop, imageRect)
        val scrim = Color(0x99000000)
        drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, rect.top))
        drawRect(
            scrim,
            topLeft = Offset(0f, rect.bottom),
            size = Size(size.width, (size.height - rect.bottom).coerceAtLeast(0f))
        )
        drawRect(
            scrim,
            topLeft = Offset(0f, rect.top),
            size = Size(rect.left, rect.height)
        )
        drawRect(
            scrim,
            topLeft = Offset(rect.right, rect.top),
            size = Size((size.width - rect.right).coerceAtLeast(0f), rect.height)
        )

        drawRect(
            color = Color.White,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = 1.5.dp.toPx())
        )

        val third = 1f / 3f
        for (i in 1..2) {
            val x = rect.left + rect.width * third * i
            val y = rect.top + rect.height * third * i
            drawLine(
                Color(0x66FFFFFF),
                Offset(x, rect.top),
                Offset(x, rect.bottom),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                Color(0x66FFFFFF),
                Offset(rect.left, y),
                Offset(rect.right, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val handleColor = Color.White
        val radius = 9.dp.toPx()
        listOf(
            rect.topLeft,
            rect.topRight,
            rect.bottomLeft,
            rect.bottomRight
        ).forEach { point ->
            drawCircle(handleColor, radius, point)
            drawCircle(Color(0xFF9C99A8), radius * 0.45f, point)
        }
    }
}

private fun cropInPixels(crop: Rect, imageRect: Rect): Rect {
    return Rect(
        left = imageRect.left + crop.left * imageRect.width,
        top = imageRect.top + crop.top * imageRect.height,
        right = imageRect.left + crop.right * imageRect.width,
        bottom = imageRect.top + crop.bottom * imageRect.height
    )
}

private fun hitHandle(offset: Offset, cropPx: Rect, radius: Float): CropHandle {
    val corners = listOf(
        CropHandle.TOP_LEFT to cropPx.topLeft,
        CropHandle.TOP_RIGHT to cropPx.topRight,
        CropHandle.BOTTOM_LEFT to cropPx.bottomLeft,
        CropHandle.BOTTOM_RIGHT to cropPx.bottomRight
    )
    corners.forEach { (handle, point) ->
        if ((offset - point).getDistance() <= radius) return handle
    }
    return if (cropPx.contains(offset)) CropHandle.MOVE else CropHandle.NONE
}

private fun moveCrop(crop: Rect, dx: Float, dy: Float): Rect {
    val width = crop.width
    val height = crop.height
    val left = (crop.left + dx).coerceIn(0f, 1f - width)
    val top = (crop.top + dy).coerceIn(0f, 1f - height)
    return Rect(left, top, left + width, top + height)
}

private fun resizeCrop(
    crop: Rect,
    dx: Float,
    dy: Float,
    ratio: Float?,
    imageWidth: Int,
    imageHeight: Int,
    leftSide: Boolean,
    topSide: Boolean
): Rect {
    val minSize = 0.08f

    if (ratio == null) {
        var left = crop.left
        var top = crop.top
        var right = crop.right
        var bottom = crop.bottom
        if (leftSide) left = (left + dx).coerceIn(0f, right - minSize)
        else right = (right + dx).coerceIn(left + minSize, 1f)
        if (topSide) top = (top + dy).coerceIn(0f, bottom - minSize)
        else bottom = (bottom + dy).coerceIn(top + minSize, 1f)
        return Rect(left, top, right, bottom)
    }

    val normalisedRatio = ratio * imageHeight.toFloat() / imageWidth.toFloat()
    val anchorX = if (leftSide) crop.right else crop.left
    val anchorY = if (topSide) crop.bottom else crop.top

    val draggedX = if (leftSide) crop.left + dx else crop.right + dx
    val draggedY = if (topSide) crop.top + dy else crop.bottom + dy

    val requestedWidth = kotlin.math.abs(draggedX - anchorX)
    val requestedHeight = kotlin.math.abs(draggedY - anchorY)

    var width = if (requestedHeight * normalisedRatio >= requestedWidth) {
        requestedHeight * normalisedRatio
    } else {
        requestedWidth
    }
    var height = width / normalisedRatio

    val maxWidth = if (leftSide) anchorX else 1f - anchorX
    val maxHeight = if (topSide) anchorY else 1f - anchorY
    width = width.coerceAtMost(maxWidth)
    if (height > maxHeight) {
        height = maxHeight
        width = height * normalisedRatio
    }
    width = width.coerceAtLeast(minSize)
    height = (width / normalisedRatio).coerceAtLeast(minSize)

    val left = if (leftSide) anchorX - width else anchorX
    val top = if (topSide) anchorY - height else anchorY
    return Rect(left, top, left + width, top + height)
}

@Composable
private fun CropPanel(
    ratio: CropRatio,
    onRatioChange: (CropRatio) -> Unit
) {
    val options = listOf(
        CropRatio.FREE to stringResource(R.string.editor_ratio_free),
        CropRatio.SQUARE to stringResource(R.string.editor_ratio_square),
        CropRatio.PORTRAIT to stringResource(R.string.editor_ratio_portrait),
        CropRatio.LANDSCAPE to stringResource(R.string.editor_ratio_landscape)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { (option, label) ->
            val selected = option == ratio
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xFF1D1D26))
                    .border(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color(0x1FFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onRatioChange(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF)
                )
            }
        }
    }
}

@Composable
private fun RotatePanel(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorRoundAction("↺", stringResource(R.string.editor_rotate_left), onRotateLeft)
        EditorRoundAction("⇆", stringResource(R.string.editor_flip), onFlip)
        EditorRoundAction("↻", stringResource(R.string.editor_rotate_right), onRotateRight)
    }
}

@Composable
private fun EditorRoundAction(glyph: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1D1D26)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xCCFFFFFF)
        )
    }
}

@Composable
private fun FiltersPanel(
    bitmap: Bitmap?,
    filter: EditorFilter,
    edits: ImageEdits,
    onFilterChange: (EditorFilter) -> Unit
) {
    val filterNames = listOf(
        EditorFilter.ORIGINAL to stringResource(R.string.editor_filter_original),
        EditorFilter.WARM to stringResource(R.string.editor_filter_warm),
        EditorFilter.COOL to stringResource(R.string.editor_filter_cool),
        EditorFilter.MONO to stringResource(R.string.editor_filter_mono),
        EditorFilter.VIVID to stringResource(R.string.editor_filter_vivid),
        EditorFilter.FADE to stringResource(R.string.editor_filter_fade)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filterNames, key = { it.first.name }) { (option, label) ->
            val selected = option == filter
            val matrix = remember(option, edits.brightness, edits.contrast, edits.saturation, edits.flipH, edits.flipV) {
                ColorFilter.colorMatrix(
                    ColorMatrix(ImageEditor.colorMatrix(edits.copy(filter = option)).array)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onFilterChange(option) }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            colorFilter = matrix,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF)
                )
            }
        }
    }
}

@Composable
private fun AdjustPanel(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.editor_tool_adjust),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xCCFFFFFF),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onReset) {
                Text(
                    text = stringResource(R.string.editor_reset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        AdjustSlider(
            label = stringResource(R.string.editor_brightness),
            value = brightness,
            onValueChange = onBrightness
        )
        AdjustSlider(
            label = stringResource(R.string.editor_contrast),
            value = contrast,
            onValueChange = onContrast
        )
        AdjustSlider(
            label = stringResource(R.string.editor_saturation),
            value = saturation,
            onValueChange = onSaturation
        )
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xCCFFFFFF),
            modifier = Modifier.width(84.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = "${(value * 100).roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0x99FFFFFF),
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun ToolBar(tool: EditorTool, onToolChange: (EditorTool) -> Unit) {
    val tools = listOf(
        EditorTool.CROP to stringResource(R.string.editor_tool_crop),
        EditorTool.ROTATE to stringResource(R.string.editor_tool_rotate),
        EditorTool.FILTERS to stringResource(R.string.editor_tool_filters),
        EditorTool.ADJUST to stringResource(R.string.editor_tool_adjust)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        tools.forEach { (entry, label) ->
            val selected = tool == entry
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else Color.Transparent
                    )
                    .clickable { onToolChange(if (selected) EditorTool.NONE else entry) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

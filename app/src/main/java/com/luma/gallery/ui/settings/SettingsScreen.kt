package com.luma.gallery.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.gallery.R
import com.luma.gallery.ui.GalleryViewModel
import com.luma.gallery.ui.components.LumaChip
import com.luma.gallery.ui.components.SectionHeader
import com.luma.gallery.ui.theme.Accent
import com.luma.gallery.ui.theme.LocalLumaPalette
import com.luma.gallery.ui.theme.ThemeMode
import com.luma.gallery.web.WebBridge
import com.luma.gallery.web.WebPrefs
import com.luma.gallery.web.WebQr
import com.luma.gallery.web.WebService

@Composable
fun SettingsScreen(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val palette = LocalLumaPalette.current
    val activity = remember(context) { context.findActivity() }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.labelLarge,
                color = palette.muted
            )
            Spacer(Modifier.height(10.dp))
        }

        item { SectionHeader(stringResource(R.string.settings_appearance), Modifier.padding(horizontal = 0.dp)) }

        item {
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_theme_dark) + " / " +
                        stringResource(R.string.settings_theme_light),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.System to stringResource(R.string.settings_theme_system),
                        ThemeMode.Dark to stringResource(R.string.settings_theme_dark),
                        ThemeMode.Light to stringResource(R.string.settings_theme_light)
                    ).forEach { (mode, label) ->
                        LumaChip(
                            text = label,
                            selected = viewModel.themeMode == mode,
                            onClick = { viewModel.updateThemeMode(mode) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_accent),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Accent.entries.forEach { accent ->
                        AccentSwatch(
                            accent = accent,
                            customArgb = viewModel.customAccentArgb,
                            selected = viewModel.accent == accent,
                            onClick = { viewModel.updateAccent(accent) }
                        )
                    }
                }

                if (viewModel.accent == Accent.Custom) {
                    Spacer(Modifier.height(18.dp))
                    CustomAccentPicker(
                        argb = viewModel.customAccentArgb,
                        onChange = { viewModel.updateCustomAccent(it) }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_grid),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (3..6).forEach { columns ->
                        LumaChip(
                            text = columns.toString(),
                            selected = viewModel.gridColumns == columns,
                            onClick = { viewModel.updateGridColumns(columns) }
                        )
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_language), Modifier.padding(horizontal = 0.dp)) }

        item {
            SettingsCard {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        null to stringResource(R.string.settings_language_system),
                        "en" to "English",
                        "te" to "తెలుగు",
                        "hi" to "हिन्दी"
                    ).forEach { (tag, label) ->
                        LumaChip(
                            text = label,
                            selected = viewModel.languageTag == tag,
                            onClick = {
                                if (viewModel.languageTag != tag) {
                                    viewModel.setLanguage(tag)
                                    activity?.recreate()
                                }
                            }
                        )
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_web), Modifier.padding(horizontal = 0.dp)) }

        item {
            WebAccessCard()
        }

        item { SectionHeader(stringResource(R.string.settings_all_files), Modifier.padding(horizontal = 0.dp)) }

        item {
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }.onFailure {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_all_files),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.settings_all_files_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = viewModel.hasAllFilesAccess,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                        onCheckedChange = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_about), Modifier.padding(horizontal = 0.dp)) }

        item {
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_version),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Luma · ${stringResource(R.string.tagline)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_connect), Modifier.padding(horizontal = 0.dp)) }

        item {
            SettingsCard {
                LinkRow(
                    title = stringResource(R.string.settings_github_title),
                    value = GITHUB_HANDLE,
                    icon = {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = { openLink(context, GITHUB_URL) }
                )
                Spacer(Modifier.height(6.dp))
                LinkRow(
                    title = stringResource(R.string.settings_email_title),
                    value = CONTACT_EMAIL,
                    icon = {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = { openLink(context, "mailto:$CONTACT_EMAIL?subject=Luma%20Gallery") }
                )
            }
        }
    }
}

private const val GITHUB_URL = "https://github.com/janakiammanike"
private const val GITHUB_HANDLE = "@janakiammanike"
private const val CONTACT_EMAIL = "janakiram25@yahoo.com"

private fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure {
            Toast.makeText(context, R.string.settings_link_failed, Toast.LENGTH_SHORT).show()
        }
}

@Composable
private fun LinkRow(
    title: String,
    value: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val palette = LocalLumaPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_open),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun WebAccessCard() {
    val context = LocalContext.current
    val palette = LocalLumaPalette.current
    val status by WebBridge.status.collectAsStateWithLifecycle()
    var showQr by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { WebService.start(context) }

    fun open(url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, R.string.web_wifi_hint, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                Toast.makeText(context, R.string.web_failed, Toast.LENGTH_SHORT).show()
            }
    }

    fun enable() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            WebService.start(context)
        }
    }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_web),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.settings_web_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = status.running || status.starting,
                onCheckedChange = { on ->
                    if (on) enable() else WebService.stop(context)
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                status.running -> stringResource(R.string.web_on)
                status.starting -> stringResource(R.string.web_starting)
                status.error != null -> stringResource(R.string.web_failed)
                else -> stringResource(R.string.web_off)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                status.error != null -> MaterialTheme.colorScheme.error
                status.running -> MaterialTheme.colorScheme.primary
                else -> palette.muted
            }
        )

        if (status.error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = status.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }

        if (status.running) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.web_open_hint),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
            if (status.urls.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.web_wifi_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
            } else {
                status.urls.forEach { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { open(address) }
                            .padding(vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LumaChip(
                    text = stringResource(R.string.web_open),
                    selected = false,
                    onClick = { open(status.urls.firstOrNull()) }
                )
                LumaChip(
                    text = stringResource(R.string.web_scan),
                    selected = false,
                    onClick = { showQr = true }
                )
                LumaChip(
                    text = stringResource(R.string.web_new_pin),
                    selected = false,
                    onClick = {
                        val fresh = WebPrefs.newPin(context)
                        WebBridge.publish(status.copy(pin = fresh))
                    }
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.web_pin),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted
                    )
                    Text(
                        text = status.pin.ifBlank { WebPrefs.pin(context) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.web_pin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
    }

    if (showQr) {
        val target = status.urls.firstOrNull()
        Dialog(onDismissRequest = { showQr = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val bitmap = remember(target) { target?.let { WebQr.bitmap(it) } }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.web_scan),
                        modifier = Modifier.size(250.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = target ?: stringResource(R.string.web_wifi_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF12101A),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.web_open_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5A5668)
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val palette = LocalLumaPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun AccentSwatch(
    accent: Accent,
    customArgb: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = accent.base(customArgb)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = accent.label,
                tint = if (color.luminance() > 0.22f) Color(0xFF120F19) else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CustomAccentPicker(argb: Int, onChange: (Int) -> Unit) {
    val palette = LocalLumaPalette.current
    val hsv = remember(argb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
    }
    var hue by remember(argb) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(argb) { mutableFloatStateOf(hsv[1]) }

    fun emit(h: Float = hue, s: Float = saturation) {
        val color = android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0.18f, 0.85f), 1f))
        onChange(color)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.settings_accent_custom),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_accent_hue),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                emit(h = it)
            },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = Color(argb),
                activeTrackColor = Color(argb)
            )
        )
        Text(
            text = stringResource(R.string.settings_accent_saturation),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )
        Slider(
            value = saturation,
            onValueChange = {
                saturation = it
                emit(s = it)
            },
            valueRange = 0.18f..0.85f,
            colors = SliderDefaults.colors(
                thumbColor = Color(argb),
                activeTrackColor = Color(argb)
            )
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

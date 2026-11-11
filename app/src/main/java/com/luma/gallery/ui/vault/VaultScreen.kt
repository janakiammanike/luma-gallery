package com.luma.gallery.ui.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.luma.gallery.R
import com.luma.gallery.ui.GalleryViewModel
import com.luma.gallery.ui.components.EmptyState
import com.luma.gallery.ui.components.MediaGrid
import com.luma.gallery.ui.theme.LocalLumaPalette
import com.luma.gallery.ui.viewer.ViewerRequest
import com.luma.gallery.ui.viewer.ViewerScreen

@Composable
fun VaultScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalLumaPalette.current
    val items = viewModel.vaultItems
    val selection = viewModel.selectedIds
    val unlocked = viewModel.vaultUnlocked
    var showUnavailable by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    val activity = remember(context) { context.findFragmentActivity() }
    val title = stringResource(R.string.vault_unlock_title)
    val subtitle = stringResource(R.string.vault_unlock_body)

    LaunchedEffect(Unit) {
        viewModel.clearSelection()
        viewModel.refreshVault()
    }

    val credentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.unlockVault()
    }

    fun authenticate() {
        val host = activity
        if (host == null) {
            showUnavailable = true
            return
        }

        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        val status = BiometricManager.from(context).canAuthenticate(authenticators)
        if (status == BiometricManager.BIOMETRIC_SUCCESS) {
            val prompt = BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        viewModel.unlockVault()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                            errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
                        ) {
                            showUnavailable = true
                        }
                    }
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
            return
        }

        // Older devices may have a screen lock but no enrolled fingerprint.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
            val intent = keyguard?.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent != null) {
                credentialLauncher.launch(intent)
                return
            }
        }

        showUnavailable = true
    }

    LaunchedEffect(Unit) {
        if (!viewModel.vaultUnlocked) authenticate()
    }

    BackHandler(enabled = viewerIndex == null) {
        viewModel.lockVault()
        onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!unlocked) {
            VaultLocked(
                onUnlock = { authenticate() },
                onBack = {
                    viewModel.lockVault()
                    onBack()
                }
            )
        } else {
            val selectedItems = items.filter { it.id in selection }

            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        )
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selection.isEmpty()) {
                        IconButton(onClick = {
                            viewModel.lockVault()
                            onBack()
                        }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.vault_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (items.isEmpty()) {
                                    stringResource(R.string.vault_note)
                                } else {
                                    stringResource(R.string.photos_count, items.size)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.muted
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = stringResource(R.string.selected_count, selection.size),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.restoreFromVault(selection) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_restore),
                                contentDescription = stringResource(R.string.action_restore),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = { confirmPurge = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete_forever),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                when {
                    !viewModel.vaultLoaded && items.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    items.isEmpty() -> EmptyState(
                        title = stringResource(R.string.vault_empty),
                        hint = stringResource(R.string.vault_empty_hint)
                    )

                    else -> MediaGrid(
                        items = items,
                        columns = viewModel.gridColumns,
                        selection = selection,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 40.dp
                        ),
                        onOpen = { index -> viewerIndex = index },
                        onLongPress = { viewModel.toggleSelection(it.id) }
                    )
                }
            }
        }

        val index = viewerIndex
        if (unlocked && index != null && index in items.indices) {
            ViewerScreen(
                viewModel = viewModel,
                request = ViewerRequest(items, index),
                onClose = { viewerIndex = null },
                vaultMode = true
            )
        }
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text(stringResource(R.string.delete_forever_title)) },
            text = { Text(stringResource(R.string.delete_forever_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmPurge = false
                    viewModel.deleteFromVaultForever(selection)
                }) {
                    Text(
                        stringResource(R.string.action_delete_forever),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showUnavailable) {
        AlertDialog(
            onDismissRequest = { showUnavailable = false },
            title = { Text(stringResource(R.string.vault_unavailable_title)) },
            text = { Text(stringResource(R.string.vault_unavailable_body)) },
            confirmButton = {
                TextButton(onClick = { showUnavailable = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun VaultLocked(onUnlock: () -> Unit, onBack: () -> Unit) {
    val palette = LocalLumaPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            )
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.vault_locked_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vault_locked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onUnlock,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.vault_unlock),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(40.dp))
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}

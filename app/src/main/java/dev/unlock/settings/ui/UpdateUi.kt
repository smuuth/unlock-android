package dev.unlock.settings.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.unlock.BuildConfig
import dev.unlock.R
import dev.unlock.unlockApp
import dev.unlock.update.UpdateState

/** Gut sichtbarer Hinweis „Update verfügbar – jetzt installieren“ inkl. Fortschritt und Erlaubnis-Dialog. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateCard(modifier: Modifier = Modifier) {
    val updates = LocalContext.current.unlockApp.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val release = state.available ?: return
    UpdatePermissionDialogHost()

    val colors = MaterialTheme.colorScheme
    Surface(color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, shape = CardShape, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(colors.tertiary, MaterialShapes.Cookie9Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_system_update), null, tint = colors.onTertiary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        stringResource(R.string.update_body, release.version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onTertiaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
            when (state.phase) {
                UpdateState.Phase.DOWNLOADING, UpdateState.Phase.INSTALLING -> {
                    val progress = state.progress
                    if (progress != null && state.phase == UpdateState.Phase.DOWNLOADING) {
                        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when {
                            state.phase == UpdateState.Phase.INSTALLING -> stringResource(R.string.update_installing)
                            progress != null -> stringResource(R.string.update_downloading_percent, (progress * 100).toInt())
                            else -> stringResource(R.string.update_downloading)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    state.error?.let { error ->
                        Text(
                            stringResource(
                                when (error) {
                                    UpdateState.UpdateError.DOWNLOAD_FAILED -> R.string.update_error_download
                                    UpdateState.UpdateError.INVALID_APK -> R.string.update_error_invalid
                                    UpdateState.UpdateError.INSTALL_FAILED -> R.string.update_error_install
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error,
                        )
                        Spacer(Modifier.size(12.dp))
                    }
                    val height = ButtonDefaults.MediumContainerHeight
                    Button(
                        onClick = updates::startUpdate,
                        shapes = ButtonDefaults.shapesFor(height),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary, contentColor = colors.onTertiary),
                        contentPadding = ButtonDefaults.contentPaddingFor(height),
                        modifier = Modifier.fillMaxWidth().heightIn(min = height),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_system_update),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            stringResource(if (state.error != null) R.string.update_retry else R.string.update_install),
                            style = ButtonDefaults.textStyleFor(height),
                        )
                    }
                }
            }
        }
    }
}

/** Erklärt die einmalige Android-Erlaubnis und führt direkt in die passende Systemeinstellung. */
@Composable
private fun UpdatePermissionDialogHost() {
    val context = LocalContext.current
    val updates = context.unlockApp.updates
    val state by updates.state.collectAsStateWithLifecycle()

    // Zurück aus den Einstellungen: Wurde die Erlaubnis erteilt, startet das Update von selbst.
    LifecycleResumeEffect(updates) {
        updates.onResume()
        onPauseOrDispose { }
    }

    if (!state.needsInstallPermission) return
    AlertDialog(
        onDismissRequest = updates::dismissPermissionHint,
        icon = { Icon(painterResource(R.drawable.ic_shield), null) },
        title = { Text(stringResource(R.string.update_permission_title)) },
        text = { Text(stringResource(R.string.update_permission_body)) },
        confirmButton = {
            TextButton(onClick = {
                // Hinweis bleibt gesetzt, damit es nach der Rückkehr automatisch weitergeht.
                runCatching { context.startActivity(updates.installPermissionIntent()) }
                    .onFailure { updates.dismissPermissionHint() }
            }) { Text(stringResource(R.string.update_permission_open)) }
        },
        dismissButton = {
            TextButton(onClick = updates::dismissPermissionHint) { Text(stringResource(R.string.stats_cancel)) }
        },
    )
}

/** Zeile „Nach Updates suchen“ in den Einstellungen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CheckForUpdatesItem() {
    val updates = LocalContext.current.unlockApp.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val checking = state.phase == UpdateState.Phase.CHECKING
    val status = when {
        checking -> stringResource(R.string.update_checking)
        state.manualResult == UpdateState.ManualResult.UP_TO_DATE ->
            stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME)
        state.manualResult == UpdateState.ManualResult.FAILED -> stringResource(R.string.update_check_failed)
        state.available != null -> stringResource(R.string.update_body, state.available!!.version)
        state.lastCheckedAt > 0 -> stringResource(
            R.string.update_last_checked,
            DateUtils.getRelativeTimeSpanString(state.lastCheckedAt).toString(),
        )
        else -> stringResource(R.string.update_auto_hint)
    }
    SegmentedListItem(
        onClick = updates::checkNow,
        enabled = !state.busy,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        colors = calmSegmentedColors(),
        leadingContent = { Icon(painterResource(R.drawable.ic_system_update), null) },
        supportingContent = { Text(status) },
        trailingContent = if (checking) {
            { LoadingIndicator(Modifier.size(32.dp)) }
        } else {
            null
        },
    ) {
        Text(stringResource(R.string.update_check))
    }
}

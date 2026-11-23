package dev.unlock.settings.ui

import android.app.Activity
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.unlock.BuildConfig
import dev.unlock.R
import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveService
import dev.unlock.archive.ArchiveTarget
import dev.unlock.settings.HistoryEntry
import dev.unlock.settings.OpenMode
import dev.unlock.settings.Settings
import dev.unlock.settings.SettingsViewModel
import dev.unlock.share.LinkProcessor
import dev.unlock.share.ShareActivity
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.subtitle)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        val current = settings
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") { HowItWorksCard() }
            item(key = "test") { TryItCard(current) }
            item(key = "service-header") { SectionHeader(stringResource(R.string.section_service)) }
            item(key = "service") { ServiceSection(current, viewModel) }
            item(key = "behavior-header") { SectionHeader(stringResource(R.string.section_behavior)) }
            item(key = "behavior") { BehaviorSection(current, viewModel) }
            item(key = "history") {
                AnimatedVisibility(
                    visible = current.historyEnabled,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    HistorySection(history, onClear = viewModel::clearHistory)
                }
            }
            item(key = "about-header") { SectionHeader(stringResource(R.string.section_about)) }
            item(key = "about") { AboutCard() }
            item(key = "bottom") {
                Column {
                    Spacer(Modifier.size(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 0.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HowItWorksCard() {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingBadge()
                Spacer(Modifier.width(20.dp))
                Text(
                    stringResource(R.string.how_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.size(20.dp))
            listOf(R.string.how_step_share, R.string.how_step_pick, R.string.how_step_read).forEachIndexed { i, res ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(res),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.how_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TryItCard(settings: Settings) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val result = remember(text, settings) { LinkProcessor.process(listOf(text), settings) }
    val open = { if (result != null) ShareActivity.openFromTexts(context as Activity, listOf(text), source = "test") }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.test_title), style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.size(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.test_field_label)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { open() }),
                trailingIcon = {
                    if (text.isEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val clip = clipboard.getClipEntry()?.clipData
                                val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                                if (!pasted.isNullOrBlank()) text = pasted.toString()
                            }
                        }) { Icon(painterResource(R.drawable.ic_paste), stringResource(R.string.test_paste)) }
                    } else {
                        IconButton(onClick = { text = "" }) {
                            Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.test_clear))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = when {
                    text.isBlank() -> " "
                    result == null -> stringResource(R.string.test_no_link)
                    else -> stringResource(R.string.test_preview, result.targetUrl)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (text.isNotBlank() && result == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 14.dp),
            )
            val height = ButtonDefaults.MediumContainerHeight
            // Expressive-Button: Shape morpht beim Drücken (rund → eckiger).
            Button(
                onClick = { open() },
                shapes = ButtonDefaults.shapesFor(height),
                enabled = result != null,
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                modifier = Modifier.fillMaxWidth().heightIn(min = height),
            ) {
                Icon(
                    painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.test_open), style = ButtonDefaults.textStyleFor(height))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ServiceSection(settings: Settings, viewModel: SettingsViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            val services = ArchiveService.entries
            ConnectedToggleRow(
                labels = services.map { it.displayName },
                selectedIndex = services.indexOf(settings.target.service),
                onSelect = { i ->
                    val service = services[i]
                    if (service != settings.target.service) viewModel.setTarget(ArchiveTarget.forService(service).first())
                },
            )

            AnimatedVisibility(
                visible = settings.target.service == ArchiveService.ARCHIVE_TODAY,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column {
                    Spacer(Modifier.size(20.dp))
                    Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.size(8.dp))
                    val modes = ArchiveTarget.forService(ArchiveService.ARCHIVE_TODAY)
                    ConnectedToggleRow(
                        labels = modes.map { stringResource(modeLabel(it)) },
                        selectedIndex = modes.indexOf(settings.target),
                        onSelect = { viewModel.setTarget(modes[it]) },
                    )
                    Spacer(Modifier.size(20.dp))
                    Text(stringResource(R.string.domain_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.domain_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ArchiveDomain.entries.forEach { domain ->
                            val checked = domain == settings.domain
                            ToggleButton(
                                checked = checked,
                                onCheckedChange = { viewModel.setDomain(domain) },
                                colors = segmentColors(),
                                modifier = Modifier.semantics { role = Role.RadioButton },
                            ) {
                                Text(domain.host, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(16.dp))
            Text(
                stringResource(targetDescription(settings.target)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Connected Button Group (Expressive): Segmente, deren Ecken beim Auswählen/Drücken morphen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedToggleRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        labels.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { onSelect(index) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = segmentColors(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Ausgewählt: kräftiges Primary; sonst ruhiges Secondary-Container – klar unterscheidbar in Hell & Dunkel. */
@Composable
private fun segmentColors() = ToggleButtonDefaults.colors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    checkedContainerColor = MaterialTheme.colorScheme.primary,
    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
)

private fun modeLabel(target: ArchiveTarget) = when (target) {
    ArchiveTarget.ARCHIVE_TODAY_NEWEST -> R.string.mode_newest
    ArchiveTarget.ARCHIVE_TODAY_SUBMIT -> R.string.mode_submit
    ArchiveTarget.ARCHIVE_TODAY_ALL -> R.string.mode_all
    ArchiveTarget.WAYBACK -> R.string.shortcut_wayback
}

private fun targetDescription(target: ArchiveTarget) = when (target) {
    ArchiveTarget.ARCHIVE_TODAY_NEWEST -> R.string.mode_newest_desc
    ArchiveTarget.ARCHIVE_TODAY_SUBMIT -> R.string.mode_submit_desc
    ArchiveTarget.ARCHIVE_TODAY_ALL -> R.string.mode_all_desc
    ArchiveTarget.WAYBACK -> R.string.wayback_desc
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BehaviorSection(settings: Settings, viewModel: SettingsViewModel) {
    data class Toggle(val title: Int, val desc: Int, val checked: Boolean, val enabled: Boolean, val onChange: (Boolean) -> Unit)

    val openModes = OpenMode.entries
    val toggles = listOf(
        Toggle(R.string.setting_clean, R.string.setting_clean_desc, settings.cleanUrls, true, viewModel::setCleanUrls),
        Toggle(R.string.setting_amp, R.string.setting_amp_desc, settings.normalizeAmp, settings.cleanUrls, viewModel::setNormalizeAmp),
        Toggle(R.string.setting_history, R.string.setting_history_desc, settings.historyEnabled, true, viewModel::setHistoryEnabled),
        Toggle(R.string.setting_analytics, R.string.setting_analytics_desc, settings.analyticsEnabled, true, viewModel::setAnalyticsEnabled),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(stringResource(R.string.setting_open_mode), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(8.dp))
                ConnectedToggleRow(
                    labels = openModes.map { stringResource(openModeLabel(it)) },
                    selectedIndex = openModes.indexOf(settings.openMode),
                    onSelect = { viewModel.setOpenMode(openModes[it]) },
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(openModeDescription(settings.openMode)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            toggles.forEachIndexed { index, t ->
                SegmentedListItem(
                    checked = t.checked,
                    onCheckedChange = t.onChange,
                    enabled = t.enabled,
                    shapes = ListItemDefaults.segmentedShapes(index, toggles.size),
                    colors = calmSegmentedColors(),
                    supportingContent = { Text(stringResource(t.desc)) },
                    trailingContent = { Switch(checked = t.checked, onCheckedChange = null, enabled = t.enabled) },
                ) {
                    Text(stringResource(t.title))
                }
            }
        }
    }
}

private fun openModeLabel(mode: OpenMode) = when (mode) {
    OpenMode.READER -> R.string.open_mode_reader
    OpenMode.CUSTOM_TAB -> R.string.open_mode_custom_tab
    OpenMode.BROWSER -> R.string.open_mode_browser
}

private fun openModeDescription(mode: OpenMode) = when (mode) {
    OpenMode.READER -> R.string.open_mode_reader_desc
    OpenMode.CUSTOM_TAB -> R.string.open_mode_custom_tab_desc
    OpenMode.BROWSER -> R.string.open_mode_browser_desc
}

/** Einheitlicher Hintergrund – der Zustand wird vom Switch angezeigt, nicht von der ganzen Zeile. */
@Composable
private fun calmSegmentedColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistorySection(history: List<HistoryEntry>, onClear: () -> Unit) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.section_history)) }
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.history_clear))
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        if (history.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CardShape) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                history.forEachIndexed { index, entry ->
                    SegmentedListItem(
                        onClick = { ShareActivity.openFromTexts(context as Activity, listOf(entry.url), source = "history") },
                        shapes = ListItemDefaults.segmentedShapes(index, history.size),
                        colors = calmSegmentedColors(),
                        leadingContent = { Icon(painterResource(R.drawable.ic_history), null) },
                        supportingContent = {
                            Text(DateUtils.getRelativeTimeSpanString(entry.timestamp).toString())
                        },
                        trailingContent = {
                            Icon(painterResource(R.drawable.ic_open_in_new), stringResource(R.string.history_open))
                        },
                    ) {
                        Text(entry.url.substringAfter("://"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AboutRow(R.drawable.ic_shield, stringResource(R.string.about_privacy))
            AboutRow(R.drawable.ic_archive, stringResource(R.string.about_archive))
            AboutRow(R.drawable.ic_info, stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
        }
    }
}

@Composable
private fun AboutRow(icon: Int, text: String) {
    Row {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

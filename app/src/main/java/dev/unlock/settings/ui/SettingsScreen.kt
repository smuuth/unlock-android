package dev.unlock.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.unlock.BuildConfig
import dev.unlock.R
import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveService
import dev.unlock.archive.ArchiveTarget
import dev.unlock.settings.OpenMode
import dev.unlock.settings.Settings
import dev.unlock.settings.SettingsViewModel

/**
 * Einstellungen in drei Stufen: wenige verständliche Schalter oben,
 * technische Details (Archivdienst, Domain, Browser) eingeklappt unter „Erweitert“.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var advancedOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back))
                    }
                },
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "reading-header") { SectionHeader(stringResource(R.string.section_reading)) }
            item(key = "reading") {
                SwitchList(
                    listOf(
                        SwitchRow(
                            R.string.setting_reader,
                            R.string.setting_reader_desc,
                            checked = current.openMode == OpenMode.READER,
                            onChange = { viewModel.setOpenMode(if (it) OpenMode.READER else OpenMode.CUSTOM_TAB) },
                        ),
                    ),
                )
            }
            item(key = "privacy-header") { SectionHeader(stringResource(R.string.section_privacy)) }
            item(key = "privacy") {
                SwitchList(
                    listOf(
                        SwitchRow(R.string.setting_clean, R.string.setting_clean_desc, current.cleanUrls, viewModel::setCleanUrls),
                        SwitchRow(R.string.setting_history, R.string.setting_history_desc, current.historyEnabled, viewModel::setHistoryEnabled),
                        SwitchRow(R.string.setting_stats, R.string.setting_stats_desc, stats?.enabled ?: true, viewModel::setStatsEnabled),
                        SwitchRow(R.string.setting_analytics, R.string.setting_analytics_desc, current.analyticsEnabled, viewModel::setAnalyticsEnabled),
                    ),
                )
            }
            item(key = "advanced-toggle") {
                AdvancedHeader(expanded = advancedOpen, onToggle = { advancedOpen = !advancedOpen })
            }
            item(key = "advanced") {
                AnimatedVisibility(
                    visible = advancedOpen,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    AdvancedSection(current, viewModel)
                }
            }
            item(key = "updates-header") { SectionHeader(stringResource(R.string.section_updates)) }
            item(key = "updates") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdateCard()
                    CheckForUpdatesItem()
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AdvancedHeader(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 270f else 90f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
    Column {
        Spacer(Modifier.size(16.dp))
        SegmentedListItem(
            onClick = onToggle,
            shapes = ListItemDefaults.segmentedShapes(0, 1),
            colors = calmSegmentedColors(),
            supportingContent = { Text(stringResource(R.string.advanced_desc)) },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.rotate(rotation)) },
        ) {
            Text(stringResource(R.string.section_advanced))
        }
    }
}

/** Archivdienst, Modus, Domain und Browser-Details – nur für Leute, die es genau wissen wollen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AdvancedSection(settings: Settings, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(stringResource(R.string.section_service), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(8.dp))
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
                        DomainPicker(settings.domain, viewModel::setDomain)
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
        SwitchList(
            listOf(
                SwitchRow(
                    R.string.setting_amp,
                    R.string.setting_amp_desc,
                    checked = settings.normalizeAmp,
                    onChange = viewModel::setNormalizeAmp,
                    enabled = settings.cleanUrls,
                ),
                SwitchRow(
                    R.string.setting_browser,
                    R.string.setting_browser_desc,
                    checked = settings.openMode == OpenMode.BROWSER,
                    onChange = { viewModel.setOpenMode(if (it) OpenMode.BROWSER else OpenMode.CUSTOM_TAB) },
                    enabled = settings.openMode != OpenMode.READER,
                ),
            ),
        )
    }
}

/** Aufklappliste statt fünf Chips – die Domain muss man nur wechseln, wenn eine ausfällt. */
@Composable
private fun DomainPicker(selected: ArchiveDomain, onSelect: (ArchiveDomain) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(selected.host)
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(18.dp).rotate(90f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ArchiveDomain.entries.forEach { domain ->
                DropdownMenuItem(
                    text = { Text(domain.host) },
                    leadingIcon = { RadioButton(selected = domain == selected, onClick = null) },
                    onClick = {
                        open = false
                        onSelect(domain)
                    },
                )
            }
        }
    }
}

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

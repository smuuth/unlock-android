package dev.unlock.settings.ui

import android.app.Activity
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.unlock.R
import dev.unlock.settings.HistoryEntry
import dev.unlock.settings.Settings
import dev.unlock.settings.SettingsActivity
import dev.unlock.settings.SettingsViewModel
import dev.unlock.share.LinkProcessor
import dev.unlock.share.ShareActivity
import dev.unlock.stats.StatsActivity
import dev.unlock.stats.UnlockStats
import dev.unlock.stats.ui.PillBarChart
import dev.unlock.unlockApp
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Startseite: bewusst nur das Nötigste – Link öffnen, Statistik-Vorschau, ggf. Verlauf.
 * Alle Einstellungen liegen auf einer eigenen Seite ([SettingsActivity]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updateState by context.unlockApp.updates.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.subtitle)) },
                actions = {
                    IconButton(onClick = { context.startActivity(SettingsActivity.createIntent(context)) }) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        // Anleitung nur, solange noch nie etwas geöffnet wurde (Verlauf/Statistik decken Bestandsnutzer ab).
        val firstRun = !current.hasUnlocked && history.isEmpty() && stats?.isEmpty != false

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (updateState.available != null) item(key = "update") { UpdateCard() }
            item(key = "unlock") { UnlockCard(current, firstRun) }
            stats?.takeIf { it.enabled && !it.isEmpty }?.let { s ->
                item(key = "stats") {
                    StatsTeaserCard(s, onClick = { context.startActivity(StatsActivity.createIntent(context)) })
                }
            }
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
            item(key = "bottom") {
                Column {
                    Spacer(Modifier.size(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
}

/** Hauptkarte: (beim ersten Start) Anleitung + Eingabefeld zum direkten Öffnen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnlockCard(settings: Settings, firstRun: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val result = remember(text, settings) { LinkProcessor.process(listOf(text), settings) }
    val open = { if (result != null) ShareActivity.openFromTexts(context as Activity, listOf(text), source = "test") }
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingBadge()
                Spacer(Modifier.width(20.dp))
                Text(
                    stringResource(if (firstRun) R.string.how_title else R.string.home_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = onContainer,
                )
            }
            Spacer(Modifier.size(20.dp))
            if (firstRun) {
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
                        Text(stringResource(res), style = MaterialTheme.typography.bodyLarge, color = onContainer)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.how_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.8f),
                )
            } else {
                Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge, color = onContainer)
            }
            Spacer(Modifier.size(20.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.test_field_label)) },
                singleLine = true,
                isError = text.isNotBlank() && result == null,
                supportingText = if (text.isNotBlank() && result == null) {
                    { Text(stringResource(R.string.test_no_link)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface,
                ),
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
            Spacer(Modifier.size(12.dp))
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

/** Kompakte Vorschau: Gesamtzahl und eine Mini-Woche – einziger Einstieg in die Statistik. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatsTeaserCard(stats: UnlockStats, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    Surface(onClick = onClick, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, shape = CardShape) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(colors.secondary, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_insights), null, tint = colors.onSecondary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.stats_teaser_title), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    stringResource(R.string.stats_teaser_body, stats.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.width(12.dp))
            PillBarChart(
                values = stats.dailySeries(today, 7).map { it.second },
                labels = List(7) { null },
                highlightIndex = 6,
                chartHeight = 36.dp,
                description = "",
                modifier = Modifier.width(64.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), null)
        }
    }
}

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

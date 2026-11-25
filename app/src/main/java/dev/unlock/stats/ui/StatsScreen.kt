package dev.unlock.stats.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.unlock.BuildConfig
import dev.unlock.R
import dev.unlock.settings.ui.ConnectedToggleRow
import dev.unlock.settings.ui.MorphingBadge
import dev.unlock.stats.StatsViewModel
import dev.unlock.stats.UnlockStats
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val today = remember { LocalDate.now() }
    val locale = LocalConfiguration.current.locales[0]
    var confirmReset by remember { mutableStateOf(false) }
    var showMore by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back))
                    }
                },
                actions = {
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = viewModel::seedDemo) {
                            Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.stats_demo))
                        }
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
        val current = stats
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !current.enabled -> item(key = "disabled") {
                    MessageCard(R.string.stats_disabled_title, R.string.stats_disabled_body)
                }
                current.isEmpty -> item(key = "empty") {
                    HeroCard(current, today, locale)
                }
                else -> {
                    item(key = "hero") { HeroCard(current, today, locale) }
                    item(key = "tiles") { TileGrid(current, today, locale) }
                    item(key = "activity") { ActivityCard(current, today, locale) }
                    // Details für Neugierige – standardmäßig eingeklappt, damit die Seite übersichtlich bleibt.
                    if (showMore) {
                        item(key = "daytime") { DaytimeCard(current, locale) }
                        item(key = "calendar") { CalendarCard(current, today) }
                        item(key = "weekdays") { WeekdayCard(current, locale) }
                        item(key = "how") { HowCard(current) }
                        if (current.hosts.isNotEmpty()) item(key = "sites") { TopSitesCard(current) }
                    }
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { showMore = !showMore }) {
                                Text(stringResource(if (showMore) R.string.stats_show_less else R.string.stats_show_more))
                            }
                        }
                    }
                }
            }
            item(key = "footer") {
                Footer(
                    enabled = current.enabled,
                    canReset = !current.isEmpty,
                    onEnabledChange = viewModel::setEnabled,
                    onReset = { confirmReset = true },
                )
            }
            item(key = "bottom") {
                Column {
                    Spacer(Modifier.size(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), null) },
            title = { Text(stringResource(R.string.stats_reset_title)) },
            text = { Text(stringResource(R.string.stats_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.reset()
                }) { Text(stringResource(R.string.stats_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.stats_cancel)) }
            },
        )
    }
}

// ---------------------------------------------------------------- Hero

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(stats: UnlockStats, today: LocalDate, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer, shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (stats.isEmpty) {
                        Text(
                            stringResource(R.string.stats_empty_title),
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = colors.onPrimaryContainer,
                        )
                    } else {
                        Text(
                            formatInt(animatedInt(stats.total).toLong(), locale),
                            style = MaterialTheme.typography.displayLargeEmphasized,
                            color = colors.onPrimaryContainer,
                        )
                        Text(
                            stringResource(R.string.stats_hero_label),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = colors.onPrimaryContainer,
                        )
                        stats.firstUnlockAt?.let { first ->
                            val date = Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()).toLocalDate()
                            Text(
                                stringResource(
                                    R.string.stats_since,
                                    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                MorphingBadge()
            }
            Spacer(Modifier.height(20.dp))
            if (stats.isEmpty) {
                Text(
                    stringResource(R.string.stats_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimaryContainer,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroChip(stringResource(R.string.stats_today, stats.countOn(today)), emphasized = stats.countOn(today) > 0)
                    HeroChip(stringResource(R.string.stats_week, stats.thisWeek(today)), emphasized = false)
                }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String, emphasized: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (emphasized) colors.primary else colors.surfaceContainerLowest.copy(alpha = 0.6f),
        contentColor = if (emphasized) colors.onPrimary else colors.onPrimaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

// ---------------------------------------------------------------- Kacheln

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TileGrid(stats: UnlockStats, today: LocalDate, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    val novels = stats.readerWords.toDouble() / UnlockStats.WORDS_PER_NOVEL
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            StatTile(
                polygon = MaterialShapes.Cookie4Sided,
                icon = R.drawable.ic_fire,
                value = formatInt(animatedInt(stats.currentStreak(today)).toLong(), locale),
                label = stringResource(R.string.stats_streak),
                sub = stringResource(R.string.stats_streak_best, stats.longestStreak()),
                container = colors.secondaryContainer,
                content = colors.onSecondaryContainer,
                accent = colors.secondary,
                onAccent = colors.onSecondary,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                polygon = MaterialShapes.Clover4Leaf,
                icon = R.drawable.ic_shield,
                value = formatInt(animatedInt(stats.trackersRemoved).toLong(), locale),
                label = stringResource(R.string.stats_trackers),
                sub = stringResource(R.string.stats_trackers_sub),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            StatTile(
                polygon = MaterialShapes.Pill,
                icon = R.drawable.ic_schedule,
                value = formatDuration(stats.readerMinutes),
                label = stringResource(R.string.stats_reading),
                sub = stringResource(R.string.stats_reading_sub, stats.readerArticles),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                polygon = MaterialShapes.Gem,
                icon = R.drawable.ic_article,
                value = formatCompact(animatedInt(stats.readerWords.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).toLong(), locale),
                label = stringResource(R.string.stats_words),
                sub = stringResource(R.string.stats_words_sub, String.format(locale, "%.1f", novels)),
                modifier = Modifier.weight(1f),
            )
        }
        stats.bestDay()?.let { (day, count) ->
            Surface(color = colors.surfaceContainer, shape = CardShape) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeIcon(MaterialShapes.Cookie9Sided, R.drawable.ic_trophy, colors.tertiary, colors.onTertiary, size = 44)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.stats_best_day), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                        Text(
                            day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(formatInt(count.toLong(), locale), style = MaterialTheme.typography.headlineMediumEmphasized, color = colors.tertiary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatTile(
    polygon: RoundedPolygon,
    icon: Int,
    value: String,
    label: String,
    sub: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.primary,
    onAccent: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Surface(color = container, contentColor = content, shape = CardShape, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(20.dp)) {
            ShapeIcon(polygon, icon, accent, onAccent, size = 44)
            Spacer(Modifier.height(16.dp))
            Text(value, style = MaterialTheme.typography.headlineMediumEmphasized, maxLines = 1)
            Text(label, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.72f))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeIcon(polygon: RoundedPolygon, icon: Int, background: Color, tint: Color, size: Int) {
    val shape: Shape = polygon.toShape()
    Box(Modifier.size(size.dp).background(background, shape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size((size * 0.48f).dp))
    }
}

// ---------------------------------------------------------------- Charts

private enum class Range(val label: Int) { WEEK(R.string.stats_range_week), MONTH(R.string.stats_range_month), YEAR(R.string.stats_range_year) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActivityCard(stats: UnlockStats, today: LocalDate, locale: Locale) {
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    val range = Range.entries[rangeIndex]
    val values: List<Int>
    val labels: List<String?>
    val summary: String
    when (range) {
        Range.WEEK, Range.MONTH -> {
            val days = if (range == Range.WEEK) 7 else 30
            val series = stats.dailySeries(today, days)
            values = series.map { it.second }
            labels = series.mapIndexed { i, (day, _) ->
                when {
                    range == Range.WEEK -> day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
                    (days - 1 - i) % 7 == 0 -> day.dayOfMonth.toString()
                    else -> null
                }
            }
            val sum = values.sum()
            summary = stringResource(R.string.stats_avg_day, sum, String.format(locale, "%.1f", sum.toDouble() / days))
        }
        Range.YEAR -> {
            val series = stats.monthlySeries(today, 12)
            values = series.map { it.second }
            labels = series.map { it.first.month.getDisplayName(TextStyle.NARROW, locale) }
            val sum = values.sum()
            summary = stringResource(R.string.stats_avg_month, sum, String.format(locale, "%.1f", sum / 12.0))
        }
    }
    ChartCard(title = stringResource(R.string.stats_activity), subtitle = summary) {
        ConnectedToggleRow(
            labels = Range.entries.map { stringResource(it.label) },
            selectedIndex = rangeIndex,
            onSelect = { rangeIndex = it },
        )
        Spacer(Modifier.height(20.dp))
        PillBarChart(
            values = values,
            labels = labels,
            highlightIndex = values.lastIndex,
            description = summary,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DaytimeCard(stats: UnlockStats, locale: Locale) {
    val peak = stats.peakHour()
    val peakLabel = peak?.let { LocalTime.of(it, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
    val chrono = when (peak) {
        null -> null
        in 5..10 -> R.string.stats_chrono_early
        in 11..14 -> R.string.stats_chrono_midday
        in 15..21 -> R.string.stats_chrono_evening
        else -> R.string.stats_chrono_night
    }
    val subtitle = peakLabel?.let { stringResource(R.string.stats_peak, it) }
    ChartCard(title = stringResource(R.string.stats_daytime), subtitle = subtitle) {
        HourDial(
            hourly = stats.hourly,
            peakHour = peak,
            description = subtitle.orEmpty(),
            modifier = Modifier.fillMaxWidth(0.86f).align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                peakLabel?.let {
                    Text(it, style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.tertiary)
                }
                chrono?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(stats: UnlockStats, today: LocalDate) {
    ChartCard(title = stringResource(R.string.stats_calendar), subtitle = null) {
        CalendarHeatmap(daily = stats.daily, today = today, weeks = 20, description = stringResource(R.string.stats_calendar))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            HeatLegend(stringResource(R.string.stats_less), stringResource(R.string.stats_more))
        }
    }
}

@Composable
private fun WeekdayCard(stats: UnlockStats, locale: Locale) {
    val totals = stats.weekdayTotals()
    val favourite = totals.indices.maxByOrNull { totals[it] }?.takeIf { totals[it] > 0 }
    val names = java.time.DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".") }
    val subtitle = favourite?.let {
        stringResource(R.string.stats_fav_weekday, java.time.DayOfWeek.entries[it].getDisplayName(TextStyle.FULL, locale))
    }
    ChartCard(title = stringResource(R.string.stats_weekdays), subtitle = subtitle) {
        PillBarChart(
            values = totals,
            labels = names,
            highlightIndex = favourite,
            chartHeight = 112.dp,
            description = subtitle.orEmpty(),
        )
    }
}

@Composable
private fun HowCard(stats: UnlockStats) {
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary, colors.outline, colors.error)
    val services = listOf(
        Segment("archive.today", stats.services["archive_today"] ?: 0, colors.primary),
        Segment("Wayback Machine", stats.services["wayback"] ?: 0, colors.tertiary),
    )
    val known = listOf("share", "process_text", "clipboard", "history", "test")
    val other = stats.sources.filterKeys { it !in known }.values.sum()
    val sources = (known.map { it to (stats.sources[it] ?: 0) } + ("other" to other))
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .mapIndexed { i, (id, count) -> Segment(stringResource(sourceLabel(id)), count, palette[i % palette.size]) }
    ChartCard(title = stringResource(R.string.stats_how), subtitle = null) {
        Text(stringResource(R.string.stats_services), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SplitBar(services)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.stats_sources), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SplitBar(sources)
    }
}

private fun sourceLabel(id: String) = when (id) {
    "share" -> R.string.source_share
    "process_text" -> R.string.source_process_text
    "clipboard" -> R.string.source_clipboard
    "history" -> R.string.source_history
    "test" -> R.string.source_test
    else -> R.string.source_other
}

@Composable
private fun TopSitesCard(stats: UnlockStats) {
    ChartCard(title = stringResource(R.string.stats_top_sites), subtitle = null) {
        RankedBars(stats.topHosts(5))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChartCard(title: String, subtitle: String?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageCard(title: Int, body: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            ShapeIcon(MaterialShapes.Cookie9Sided, R.drawable.ic_insights, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, 56)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmallEmphasized)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Footer

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Footer(enabled: Boolean, canReset: Boolean, onEnabledChange: (Boolean) -> Unit, onReset: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val segmented = ListItemDefaults.segmentedColors(
        containerColor = colors.surfaceContainer,
        selectedContainerColor = colors.surfaceContainer,
        selectedContentColor = colors.onSurface,
        selectedSupportingContentColor = colors.onSurfaceVariant,
        selectedTrailingContentColor = colors.onSurfaceVariant,
        disabledContainerColor = colors.surfaceContainer,
    )
    val count = if (canReset) 2 else 1
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.padding(horizontal = 8.dp)) {
            Icon(painterResource(R.drawable.ic_shield), null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.stats_privacy), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                shapes = ListItemDefaults.segmentedShapes(0, count),
                colors = segmented,
                supportingContent = { Text(stringResource(R.string.stats_enabled_desc)) },
                trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
            ) { Text(stringResource(R.string.stats_enabled)) }
            if (canReset) {
                SegmentedListItem(
                    onClick = onReset,
                    shapes = ListItemDefaults.segmentedShapes(1, count),
                    colors = segmented,
                    leadingContent = { Icon(painterResource(R.drawable.ic_delete), null, tint = colors.error) },
                ) { Text(stringResource(R.string.stats_reset), color = colors.error) }
            }
        }
    }
}

// ---------------------------------------------------------------- Helfer

/** Zählt beim ersten Anzeigen hoch – und federt bei späteren Änderungen weiter. */
@Composable
internal fun animatedInt(target: Int): Int {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target.toFloat(), tween(1_100, easing = FastOutSlowInEasing)) }
    return anim.value.roundToInt()
}

private fun formatInt(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

private fun formatCompact(value: Long, locale: Locale): String = when {
    value >= 1_000_000 -> String.format(locale, "%.1f M", value / 1_000_000.0)
    value >= 10_000 -> String.format(locale, "%.1f k", value / 1_000.0)
    else -> formatInt(value, locale)
}

@Composable
private fun formatDuration(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.stats_minutes, minutes)
    else stringResource(R.string.stats_hours, minutes / 60, minutes % 60)

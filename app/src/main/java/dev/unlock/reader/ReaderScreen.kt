package dev.unlock.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.unlock.R
import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveTarget
import dev.unlock.archive.TargetUrlBuilder
import dev.unlock.settings.Settings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface ReaderState {
    data object Loading : ReaderState
    data class ArticleReady(val article: Article) : ReaderState
    data class Interactive(val reason: InteractionReason?) : ReaderState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderScreen(
    initialArchiveUrl: String,
    originalUrl: String,
    initialDomain: ArchiveDomain,
    settings: Settings,
    onUpdateTheme: (ReaderTheme) -> Unit,
    onUpdateFont: (ReaderFont) -> Unit,
    onUpdateFontScale: (Float) -> Unit,
    onTrackEvent: (String, Map<String, String>) -> Unit = { _, _ -> },
    onArticleRead: (Article) -> Unit = {},
    onOpenInBrowser: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val palette = readerPalette(settings.readerTheme, systemDark)

    var currentDomain by remember { mutableStateOf(initialDomain) }
    var currentArchiveUrl by remember { mutableStateOf(initialArchiveUrl) }
    var webProgress by remember { mutableFloatStateOf(0.15f) }
    var state by remember { mutableStateOf<ReaderState>(ReaderState.Loading) }
    var forceWebView by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var userAgent by remember { mutableStateOf<String?>(null) }
    var extractionJob by remember { mutableStateOf<Job?>(null) }
    var mainFrameErrorUrl by remember { mutableStateOf<String?>(null) }
    // Pro Leseansicht nur einmal zählen, auch wenn erneut extrahiert wird.
    var articleCounted by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val readingProgress by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 1) 0f
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                (lastVisible.toFloat() / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }

    val showingArticle = state is ReaderState.ArticleReady && !forceWebView
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    fun loadUrlInWebView(url: String) {
        extractionJob?.cancel()
        mainFrameErrorUrl = null
        currentArchiveUrl = url
        forceWebView = false
        state = ReaderState.Loading
        webProgress = 0.1f
        webViewRef?.loadUrl(url)
    }

    fun nextDomain(): ArchiveDomain {
        val all = ArchiveDomain.entries
        return all[(all.indexOf(currentDomain) + 1) % all.size]
    }

    fun switchToWayback() {
        onTrackEvent("Unlock.Reader.action", mapOf("action" to "wayback"))
        loadUrlInWebView(TargetUrlBuilder.build(originalUrl, ArchiveTarget.WAYBACK))
    }

    fun switchToNextDomain() {
        val next = nextDomain()
        currentDomain = next
        onTrackEvent("Unlock.Reader.action", mapOf("action" to "other_domain"))
        loadUrlInWebView(TargetUrlBuilder.build(originalUrl, ArchiveTarget.ARCHIVE_TODAY_NEWEST, next))
    }

    fun submitFreshSnapshot() {
        onTrackEvent("Unlock.Reader.action", mapOf("action" to "rearchive"))
        loadUrlInWebView(TargetUrlBuilder.build(originalUrl, ArchiveTarget.ARCHIVE_TODAY_SUBMIT, currentDomain))
    }

    fun runExtraction(view: WebView, loadedUrl: String) {
        extractionJob?.cancel()
        extractionJob = scope.launch {
            delay(450)
            if (loadedUrl != currentArchiveUrl || mainFrameErrorUrl != null) return@launch
            view.evaluateJavascript(ArticleExtractorScript.JS) { raw ->
                if (loadedUrl != currentArchiveUrl || mainFrameErrorUrl != null) return@evaluateJavascript
                when (val result = ArticleParser.parse(raw, originalUrl, loadedUrl)) {
                    is ExtractionResult.ArticleFound -> {
                        state = ReaderState.ArticleReady(result.article)
                        if (!articleCounted) {
                            articleCounted = true
                            onArticleRead(result.article)
                        }
                        val service = if ("web.archive.org" in loadedUrl) "wayback" else "archive.today"
                        onTrackEvent(
                            "Unlock.Reader.extracted",
                            mapOf(
                                "service" to service,
                                "theme" to settings.readerTheme.id,
                                "font" to settings.readerFont.id,
                            ),
                        )
                    }
                    is ExtractionResult.NeedsInteraction -> {
                        forceWebView = false
                        state = ReaderState.Interactive(result.reason)
                        onTrackEvent(
                            "Unlock.Reader.interaction",
                            mapOf("reason" to result.reason.name.lowercase()),
                        )
                    }
                    ExtractionResult.NotAnArticle -> {
                        state = ReaderState.Interactive(null)
                        onTrackEvent("Unlock.Reader.interaction", mapOf("reason" to "not_article"))
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            extractionJob?.cancel()
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    val hostTitle = remember(originalUrl) { ArticleParser.extractHost(originalUrl) ?: originalUrl }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(if (showingArticle) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
        containerColor = palette.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            Column {
                TopAppBar(
                    scrollBehavior = if (showingArticle) scrollBehavior else null,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.background,
                        scrolledContainerColor = palette.surface,
                        navigationIconContentColor = palette.text,
                        titleContentColor = palette.text,
                        actionIconContentColor = palette.text,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.reader_back))
                        }
                    },
                    title = {
                        Text(
                            text = hostTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (state is ReaderState.ArticleReady) {
                            if (!forceWebView) {
                                IconButton(onClick = { showAppearanceSheet = true }) {
                                    Icon(
                                        painterResource(R.drawable.ic_text_format),
                                        stringResource(R.string.reader_appearance),
                                    )
                                }
                            }
                            IconButton(onClick = { forceWebView = !forceWebView }) {
                                Icon(
                                    painterResource(if (forceWebView) R.drawable.ic_article else R.drawable.ic_web),
                                    stringResource(if (forceWebView) R.string.reader_show_reader else R.string.reader_show_original),
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.reader_more))
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_menu_wayback)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_swap_horiz), null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        switchToWayback()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_menu_other_domain, nextDomain().host)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_swap_horiz), null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        switchToNextDomain()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_menu_rearchive)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_refresh), null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        submitFreshSnapshot()
                                    },
                                )
                                HorizontalDivider()
                                // Archiv-Link teilen: so können auch Empfänger ohne Unlock den Artikel lesen.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_share)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_share), null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onShareUrl(currentArchiveUrl)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reader_menu_open_browser)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenInBrowser(currentArchiveUrl)
                                    },
                                )
                            }
                        }
                    },
                )
                if (state is ReaderState.Loading && webProgress < 0.99f) {
                    LinearProgressIndicator(
                        progress = { webProgress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(2.5.dp),
                        color = palette.accent,
                        trackColor = Color.Transparent,
                    )
                } else if (showingArticle) {
                    LinearProgressIndicator(
                        progress = { readingProgress },
                        modifier = Modifier.fillMaxWidth().height(2.5.dp),
                        color = palette.accent,
                        trackColor = palette.divider.copy(alpha = 0.35f),
                    )
                }
            }
        },
    ) { padding ->
        val showLiveWebView = forceWebView || state is ReaderState.Interactive

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            // WebView bleibt im Baum gemountet (INVISIBLE statt GONE), damit Cookies,
            // DOM-Layout und Original-Ansicht verzögerungsfrei erhalten bleiben.
            Column(Modifier.fillMaxSize()) {
                val interactionReason = (state as? ReaderState.Interactive)?.reason
                if (showLiveWebView && interactionReason != null && !forceWebView) {
                    InteractionBanner(
                        reason = interactionReason,
                        palette = palette,
                        onArchiveNow = ::submitFreshSnapshot,
                        onTryWayback = ::switchToWayback,
                        onTryOtherDomain = ::switchToNextDomain,
                        onRetry = { loadUrlInWebView(currentArchiveUrl) },
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createConfiguredWebView(
                            context = ctx,
                            onProgress = { p -> webProgress = p / 100f },
                            onPageStarted = { url ->
                                extractionJob?.cancel()
                                mainFrameErrorUrl = null
                                if (state is ReaderState.ArticleReady &&
                                    url.substringBefore('#') != currentArchiveUrl.substringBefore('#')
                                ) {
                                    forceWebView = false
                                }
                                currentArchiveUrl = url
                                if (state !is ReaderState.Interactive) {
                                    state = ReaderState.Loading
                                }
                            },
                            onPageFinished = { view, url ->
                                if (mainFrameErrorUrl != null) return@createConfiguredWebView
                                currentArchiveUrl = url
                                runExtraction(view, url)
                            },
                            onHistoryUpdated = { view, url ->
                                if (url != currentArchiveUrl && mainFrameErrorUrl == null) {
                                    currentArchiveUrl = url
                                    runExtraction(view, url)
                                }
                            },
                            onMainFrameError = { failedUrl ->
                                mainFrameErrorUrl = failedUrl.ifBlank { currentArchiveUrl }
                                extractionJob?.cancel()
                                forceWebView = false
                                state = ReaderState.Interactive(InteractionReason.LOAD_ERROR)
                                onTrackEvent("Unlock.Reader.interaction", mapOf("reason" to "load_error"))
                            },
                        ).also { wv ->
                            webViewRef = wv
                            userAgent = wv.settings.userAgentString
                            wv.loadUrl(initialArchiveUrl)
                        }
                    },
                    update = { wv ->
                        wv.visibility = if (showLiveWebView) View.VISIBLE else View.INVISIBLE
                    },
                )
            }

            // Ladeansicht
            if (state is ReaderState.Loading && !forceWebView) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        LoadingIndicator()
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.reader_loading),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.text,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = hostTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { forceWebView = true }) {
                                Text(stringResource(R.string.reader_show_original))
                            }
                            TextButton(onClick = ::switchToWayback) {
                                Text(stringResource(R.string.shortcut_wayback))
                            }
                        }
                    }
                }
            }

            // Native Editorial-Leseansicht
            val article = (state as? ReaderState.ArticleReady)?.article
            if (article != null && !forceWebView) {
                ArticleReaderContent(
                    article = article,
                    palette = palette,
                    font = settings.readerFont,
                    fontScale = settings.readerFontScale,
                    userAgent = userAgent,
                    listState = listState,
                    onOpenLink = onOpenInBrowser,
                    onShowOriginal = { forceWebView = true },
                    onSwitchWayback = ::switchToWayback,
                    onShare = { onShareUrl(currentArchiveUrl) },
                )
            }
        }
    }

    if (showAppearanceSheet) {
        AppearanceBottomSheet(
            currentTheme = settings.readerTheme,
            currentFont = settings.readerFont,
            currentFontScale = settings.readerFontScale,
            onSelectTheme = onUpdateTheme,
            onSelectFont = onUpdateFont,
            onChangeFontScale = onUpdateFontScale,
            onDismiss = { showAppearanceSheet = false },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: android.content.Context,
    onProgress: (Int) -> Unit,
    onPageStarted: (String) -> Unit,
    onPageFinished: (WebView, String) -> Unit,
    onHistoryUpdated: (WebView, String) -> Unit,
    onMainFrameError: (String) -> Unit,
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    val webView = this
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
    }
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = true
        displayZoomControls = false
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            onProgress(newProgress)
        }
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (!url.isNullOrBlank()) onPageStarted(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (view != null && !url.isNullOrBlank()) onPageFinished(view, url)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            if (view != null && !url.isNullOrBlank() && !isReload) onHistoryUpdated(view, url)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame == true) {
                onMainFrameError(request.url?.toString().orEmpty())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InteractionBanner(
    reason: InteractionReason,
    palette: ReaderPalette,
    onArchiveNow: () -> Unit,
    onTryWayback: () -> Unit,
    onTryOtherDomain: () -> Unit,
    onRetry: () -> Unit,
) {
    val message = when (reason) {
        InteractionReason.CAPTCHA -> stringResource(R.string.reader_banner_captcha)
        InteractionReason.NO_RESULTS -> stringResource(R.string.reader_banner_no_results)
        InteractionReason.SUBMIT_FORM -> stringResource(R.string.reader_banner_submit_form)
        InteractionReason.SUBMITTING -> stringResource(R.string.reader_banner_submitting)
        InteractionReason.LOAD_ERROR -> stringResource(R.string.reader_banner_load_error)
    }
    Surface(
        color = palette.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text,
            )
            if (reason != InteractionReason.SUBMITTING) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (reason) {
                        InteractionReason.NO_RESULTS -> {
                            FilledTonalButton(onClick = onArchiveNow) {
                                Text(stringResource(R.string.reader_banner_archive_now))
                            }
                            OutlinedButton(onClick = onTryWayback) {
                                Text(stringResource(R.string.reader_menu_wayback))
                            }
                            OutlinedButton(onClick = onTryOtherDomain) {
                                Text(stringResource(R.string.reader_banner_other_domain))
                            }
                        }
                        InteractionReason.SUBMIT_FORM -> {
                            FilledTonalButton(onClick = onArchiveNow) {
                                Text(stringResource(R.string.reader_banner_archive_now))
                            }
                            OutlinedButton(onClick = onTryWayback) {
                                Text(stringResource(R.string.reader_menu_wayback))
                            }
                        }
                        InteractionReason.CAPTCHA -> {
                            FilledTonalButton(onClick = onTryWayback) {
                                Text(stringResource(R.string.reader_menu_wayback))
                            }
                            OutlinedButton(onClick = onTryOtherDomain) {
                                Text(stringResource(R.string.reader_banner_other_domain))
                            }
                        }
                        InteractionReason.LOAD_ERROR -> {
                            FilledTonalButton(onClick = onTryOtherDomain) {
                                Text(stringResource(R.string.reader_banner_other_domain))
                            }
                            OutlinedButton(onClick = onTryWayback) {
                                Text(stringResource(R.string.reader_menu_wayback))
                            }
                            OutlinedButton(onClick = onRetry) {
                                Text(stringResource(R.string.reader_banner_retry))
                            }
                        }
                        InteractionReason.SUBMITTING -> Unit
                    }
                }
            }
        }
    }
    HorizontalDivider(color = palette.divider)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleReaderContent(
    article: Article,
    palette: ReaderPalette,
    font: ReaderFont,
    fontScale: Float,
    userAgent: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenLink: (String) -> Unit,
    onShowOriginal: () -> Unit,
    onSwitchWayback: () -> Unit,
    onShare: () -> Unit,
) {
    val bodySize = (18f * fontScale).sp
    val bodyLineHeight = (18f * fontScale * 1.62f).sp
    val titleSize = (28f * fontScale).sp
    val titleLineHeight = (28f * fontScale * 1.22f).sp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header: Quelle + Lesezeit + Titel + Autor/Datum
                item(key = "article-header") {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            article.siteName?.let { site ->
                                Surface(
                                    color = palette.accent.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = site.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.reader_reading_time, article.readingTimeMinutes),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.secondaryText,
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = article.title,
                            fontFamily = font.family,
                            fontSize = titleSize,
                            lineHeight = titleLineHeight,
                            fontWeight = FontWeight.Bold,
                            color = palette.text,
                        )

                        val metaParts = listOfNotNull(article.byline, article.publishedDate)
                        if (metaParts.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = metaParts.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.secondaryText,
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = palette.divider)
                    }
                }

                // Artikel-Blöcke
                items(article.blocks) { block ->
                    when (block) {
                        is ArticleBlock.Paragraph -> {
                            val annotated = remember(block, palette.accent) {
                                buildAnnotatedString {
                                    for (span in block.spans) {
                                        val style = SpanStyle(
                                            fontWeight = if (span.bold) FontWeight.Bold else null,
                                            fontStyle = if (span.italic) FontStyle.Italic else null,
                                        )
                                        val link = span.link
                                        if (link != null) {
                                            pushLink(
                                                LinkAnnotation.Url(
                                                    url = link,
                                                    styles = TextLinkStyles(
                                                        style = style.copy(
                                                            color = palette.accent,
                                                            textDecoration = TextDecoration.Underline,
                                                        ),
                                                    ),
                                                    linkInteractionListener = { onOpenLink(link) },
                                                ),
                                            )
                                            append(span.text)
                                            pop()
                                        } else {
                                            pushStyle(style)
                                            append(span.text)
                                            pop()
                                        }
                                    }
                                }
                            }
                            Text(
                                text = annotated,
                                fontFamily = font.family,
                                fontSize = bodySize,
                                lineHeight = bodyLineHeight,
                                color = palette.text,
                            )
                        }

                        is ArticleBlock.Heading -> {
                            val size = when (block.level) {
                                2 -> (22f * fontScale).sp
                                3 -> (19.5f * fontScale).sp
                                else -> (18f * fontScale).sp
                            }
                            Text(
                                text = block.text,
                                fontFamily = font.family,
                                fontSize = size,
                                lineHeight = (size.value * 1.3f).sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.text,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }

                        is ArticleBlock.Image -> {
                            ArticleImageBlock(
                                block = block,
                                referer = article.archiveUrl,
                                userAgent = userAgent,
                                palette = palette,
                            )
                        }

                        is ArticleBlock.Quote -> {
                            Surface(
                                color = palette.quoteBackground,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.5.dp)
                                            .heightIn(min = 24.dp)
                                            .clip(CircleShape)
                                            .background(palette.accent),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        text = block.text,
                                        fontFamily = font.family,
                                        fontSize = bodySize,
                                        lineHeight = bodyLineHeight,
                                        fontStyle = FontStyle.Italic,
                                        color = palette.text.copy(alpha = 0.9f),
                                    )
                                }
                            }
                        }

                        is ArticleBlock.BulletList -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                block.items.forEachIndexed { idx, item ->
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(
                                            text = if (block.ordered) "${idx + 1}." else "•",
                                            fontFamily = font.family,
                                            fontSize = bodySize,
                                            lineHeight = bodyLineHeight,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.accent,
                                            modifier = Modifier.width(24.dp),
                                        )
                                        Text(
                                            text = item,
                                            fontFamily = font.family,
                                            fontSize = bodySize,
                                            lineHeight = bodyLineHeight,
                                            color = palette.text,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Artikel-Ende mit schnellen Aktionen
                item(key = "article-footer") {
                    Column(Modifier.padding(top = 20.dp)) {
                        HorizontalDivider(color = palette.divider)
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.reader_footer_source,
                                        ArticleParser.extractHost(article.archiveUrl) ?: "archive",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.secondaryText,
                                )
                                Spacer(Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilledTonalButton(onClick = onShowOriginal) {
                                        Text(stringResource(R.string.reader_show_original))
                                    }
                                    OutlinedButton(onClick = onSwitchWayback) {
                                        Text(stringResource(R.string.shortcut_wayback))
                                    }
                                    OutlinedButton(onClick = onShare) {
                                        Text(stringResource(R.string.reader_share))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleImageBlock(
    block: ArticleBlock.Image,
    referer: String,
    userAgent: String?,
    palette: ReaderPalette,
) {
    var bitmap by remember(block.src) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(block.src) { mutableStateOf(false) }

    LaunchedEffect(block.src) {
        val loaded = ArticleImageLoader.load(block.src, referer, userAgent)
        if (loaded != null) bitmap = loaded else failed = true
    }

    if (failed) return

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val img = bitmap
        if (img != null) {
            val aspect = (img.width.toFloat() / img.height.coerceAtLeast(1)).coerceIn(0.6f, 2.4f)
            Image(
                bitmap = img,
                contentDescription = block.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface),
            )
        }
        block.caption?.let { cap ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = cap,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = palette.secondaryText,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearanceBottomSheet(
    currentTheme: ReaderTheme,
    currentFont: ReaderFont,
    currentFontScale: Float,
    onSelectTheme: (ReaderTheme) -> Unit,
    onSelectFont: (ReaderFont) -> Unit,
    onChangeFontScale: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_appearance),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )

            // Farbschema
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.reader_theme_title), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val options = listOf(
                        Triple(ReaderTheme.SYSTEM, R.string.reader_theme_system, MaterialTheme.colorScheme.surfaceContainerHigh),
                        Triple(ReaderTheme.LIGHT, R.string.reader_theme_light, Color(0xFFFFFFFF)),
                        Triple(ReaderTheme.SEPIA, R.string.reader_theme_sepia, Color(0xFFF6EFE2)),
                        Triple(ReaderTheme.DARK, R.string.reader_theme_dark, Color(0xFF16161A)),
                    )
                    options.forEach { (theme, labelRes, swatchBg) ->
                        val selected = theme == currentTheme
                        val swatchText = if (theme == ReaderTheme.DARK) Color(0xFFE5E3E8) else Color(0xFF201E24)
                        Surface(
                            color = swatchBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectTheme(theme) },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (theme == ReaderTheme.SYSTEM) MaterialTheme.colorScheme.onSurface else swatchText,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Schriftart
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.reader_font_title), style = MaterialTheme.typography.labelLarge)
                val fonts = ReaderFont.entries
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    fonts.forEachIndexed { index, font ->
                        ToggleButton(
                            checked = font == currentFont,
                            onCheckedChange = { onSelectFont(font) },
                            shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                            else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            colors = ToggleButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                        ) {
                            Text(
                                text = stringResource(
                                    if (font == ReaderFont.SERIF) R.string.reader_font_serif else R.string.reader_font_sans,
                                ),
                                fontFamily = font.family,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            // Schriftgröße
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.reader_font_size_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${(currentFontScale * 100).roundToInt()} %",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("A", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = currentFontScale,
                        onValueChange = { v ->
                            val stepped = (v * 20f).roundToInt() / 20f
                            onChangeFontScale(stepped.coerceIn(0.85f, 1.45f))
                        },
                        valueRange = 0.85f..1.45f,
                        steps = 11,
                        modifier = Modifier.weight(1f),
                    )
                    Text("A", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

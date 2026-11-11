package com.nuvio.app.features.magnet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.features.p2p.P2pConsentDialog
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.p2p.P2pStreamingEngine
import com.nuvio.app.features.p2p.P2pTorrentFile
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressUiState
import com.nuvio.app.navigation.LocalUseNativeNavigation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.episode_mark_unwatched
import nuvio.composeapp.generated.resources.episode_mark_watched
import nuvio.composeapp.generated.resources.magnet_episode
import nuvio.composeapp.generated.resources.magnet_episodes_range
import nuvio.composeapp.generated.resources.magnet_files_error
import nuvio.composeapp.generated.resources.magnet_hash
import nuvio.composeapp.generated.resources.magnet_invalid_body
import nuvio.composeapp.generated.resources.magnet_invalid_title
import nuvio.composeapp.generated.resources.magnet_loading
import nuvio.composeapp.generated.resources.magnet_loading_files
import nuvio.composeapp.generated.resources.magnet_missing_key
import nuvio.composeapp.generated.resources.magnet_movie
import nuvio.composeapp.generated.resources.magnet_no_match_body
import nuvio.composeapp.generated.resources.magnet_no_match_title
import nuvio.composeapp.generated.resources.magnet_p2p_disabled
import nuvio.composeapp.generated.resources.magnet_p2p_unavailable
import nuvio.composeapp.generated.resources.magnet_play
import nuvio.composeapp.generated.resources.magnet_search_action
import nuvio.composeapp.generated.resources.magnet_search_another
import nuvio.composeapp.generated.resources.magnet_search_hint
import nuvio.composeapp.generated.resources.magnet_search_section_body
import nuvio.composeapp.generated.resources.magnet_section_episodes
import nuvio.composeapp.generated.resources.magnet_section_file
import nuvio.composeapp.generated.resources.magnet_section_files
import nuvio.composeapp.generated.resources.magnet_series
import nuvio.composeapp.generated.resources.magnet_size
import nuvio.composeapp.generated.resources.magnet_title
import nuvio.composeapp.generated.resources.magnet_trackers
import nuvio.composeapp.generated.resources.magnet_unavailable
import nuvio.composeapp.generated.resources.magnet_view_details
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun MagnetScreen(
    url: String,
    onBack: () -> Unit,
    onPlay: (MagnetResolution, MagnetPlaybackTarget?) -> Unit,
    onOpenDetails: (MagnetMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val magnet = remember(url) { MagnetLink.parse(url) }
    var resolution by remember(url) { mutableStateOf<MagnetResolution?>(null) }
    var isLoading by remember(url) { mutableStateOf(magnet != null) }
    var searchQuery by remember(url) { mutableStateOf("") }
    var showSearch by remember(url) { mutableStateOf(false) }
    var showP2pConsent by remember { mutableStateOf(false) }
    var pendingPlayTarget by remember { mutableStateOf<MagnetPlaybackTarget?>(null) }
    var torrentFiles by remember(url) { mutableStateOf(emptyList<P2pTorrentFile>()) }
    var isLoadingFiles by remember(url) { mutableStateOf(false) }
    var filesError by remember(url) { mutableStateOf(false) }
    val p2pSettings by remember {
        P2pSettingsRepository.ensureLoaded()
        P2pSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchProgress by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val background = MaterialTheme.colorScheme.background
    val match = resolution?.match
    val fallbackSeason = resolution?.hint?.season ?: match?.seasonNumber
    val fileRows = remember(torrentFiles, fallbackSeason) {
        buildMagnetFileRows(torrentFiles, fallbackSeason)
    }

    fun resolve(query: String? = null) {
        val link = magnet ?: return
        scope.launch {
            isLoading = true
            resolution = MagnetMetadataService.resolve(link, query)
            isLoading = false
            showSearch = false
        }
    }

    LaunchedEffect(url) {
        val link = magnet
        if (link == null) {
            isLoading = false
            return@LaunchedEffect
        }
        resolution = MagnetMetadataService.resolve(link)
        isLoading = false
    }

    LaunchedEffect(url, p2pSettings.p2pEnabled) {
        val link = magnet ?: return@LaunchedEffect
        if (!P2pSettingsRepository.isVisible || !p2pSettings.p2pEnabled) {
            torrentFiles = emptyList()
            isLoadingFiles = false
            filesError = false
            return@LaunchedEffect
        }
        isLoadingFiles = true
        filesError = false
        try {
            torrentFiles = P2pStreamingEngine.listFiles(link.infoHash, link.trackers)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            torrentFiles = emptyList()
            filesError = true
        } finally {
            isLoadingFiles = false
        }
    }

    fun play(target: MagnetPlaybackTarget?) {
        if (!P2pSettingsRepository.isVisible) return
        val current = resolution ?: return
        if (!p2pSettings.p2pEnabled) {
            pendingPlayTarget = target
            showP2pConsent = true
            return
        }
        onPlay(current, target)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        (match?.backdropUrl ?: match?.episodeThumbnail)?.let { backdrop ->
            NuvioAsyncImage(
                imageUrl = backdrop,
                contentDescription = "",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.35f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to background.copy(alpha = 0.25f),
                            0.45f to background.copy(alpha = 0.85f),
                            1.0f to background,
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!LocalUseNativeNavigation.current) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.magnet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                magnet == null -> MagnetMessage(
                    title = stringResource(Res.string.magnet_invalid_title),
                    body = stringResource(Res.string.magnet_invalid_body),
                )

                isLoading -> LoadingState()

                resolution != null -> {
                    val currentResolution = resolution!!
                    MatchContent(
                        magnet = magnet,
                        resolution = currentResolution,
                        p2pEnabled = p2pSettings.p2pEnabled,
                        fileRows = fileRows,
                        isLoadingFiles = isLoadingFiles,
                        filesError = filesError,
                        watchedKeys = watchedState.watchedKeys,
                        watchProgress = watchProgress,
                        onPlay = ::play,
                        onOpenDetails = { currentResolution.match?.let(onOpenDetails) },
                        onSearchAnother = {
                            searchQuery = currentResolution.hint?.title.orEmpty()
                            showSearch = true
                        },
                    )
                }
            }

            if (!isLoading && magnet != null &&
                resolution?.failure != MagnetResolveFailure.MissingTmdbKey &&
                (showSearch || match == null)
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                SearchSection(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { resolve(searchQuery.takeIf { it.isNotBlank() }) },
                )
            }

            if (!isLoading && match == null) {
                resolution?.failure?.let { failure ->
                    Spacer(modifier = Modifier.height(12.dp))
                    FailureMessage(failure)
                }
            }
        }
    }

    if (showP2pConsent) {
        val target = pendingPlayTarget
        P2pConsentDialog(
            onEnableP2p = {
                P2pSettingsRepository.setP2pEnabled(true)
                showP2pConsent = false
                pendingPlayTarget = null
                resolution?.let { onPlay(it, target) }
            },
            onDismiss = {
                showP2pConsent = false
                pendingPlayTarget = null
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(Res.string.magnet_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MatchContent(
    magnet: MagnetLink,
    resolution: MagnetResolution,
    p2pEnabled: Boolean,
    fileRows: List<MagnetFileRow>,
    isLoadingFiles: Boolean,
    filesError: Boolean,
    watchedKeys: Set<String>,
    watchProgress: WatchProgressUiState,
    onPlay: (MagnetPlaybackTarget?) -> Unit,
    onOpenDetails: () -> Unit,
    onSearchAnother: () -> Unit,
) {
    val match = resolution.match
    val title = match?.title ?: resolution.hint?.title ?: magnet.displayName ?: stringResource(Res.string.magnet_title)
    val subtitleParts = listOfNotNull(
        match?.year?.toString(),
        match?.kind?.let {
            if (it == MagnetMediaKind.SERIES) stringResource(Res.string.magnet_series) else stringResource(Res.string.magnet_movie)
        },
        match?.rating?.let { rating -> "★ ${formatRating(rating)}" },
        match?.runtimeMinutes?.takeIf { it > 0 }?.let { "${it}m" },
    )
    val batchRange = if (match?.kind != MagnetMediaKind.MOVIE) {
        fileRows.episodeRange() ?: resolution.hint?.episodeRange
    } else {
        null
    }
    val episodeLine = if (batchRange != null) {
        stringResource(Res.string.magnet_episodes_range, batchRange.first, batchRange.last)
    } else {
        listOfNotNull(
            match?.episodeLabel?.let { label -> "${stringResource(Res.string.magnet_episode)} $label" },
            match?.episodeTitle,
        ).joinToString(" · ")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(124.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val posterUrl = match?.posterUrl
            if (posterUrl != null) {
                NuvioAsyncImage(
                    imageUrl = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitleParts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitleParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (episodeLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = episodeLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            match?.episodeAirDate?.let { airDate ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = airDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    match?.summary?.let { summary ->
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    TorrentDetails(
        magnet = magnet,
        title = title,
        metaId = match?.metaId ?: "magnet:${magnet.infoHash}",
        metaType = match?.kind?.metaType ?: MagnetMediaKind.SERIES.metaType,
        fileRows = fileRows,
        isLoadingFiles = isLoadingFiles,
        filesError = filesError,
        watchedKeys = watchedKeys,
        watchProgress = watchProgress,
        onPlay = onPlay,
    )

    if (match != null) {
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onOpenDetails,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.magnet_view_details))
        }
    }

    if (!P2pSettingsRepository.isVisible) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.magnet_p2p_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else if (!p2pEnabled) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.magnet_p2p_disabled),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (match != null) {
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onSearchAnother) {
            Text(text = stringResource(Res.string.magnet_search_another))
        }
    }
}

@Composable
private fun TorrentDetails(
    magnet: MagnetLink,
    title: String,
    metaId: String,
    metaType: String,
    fileRows: List<MagnetFileRow>,
    isLoadingFiles: Boolean,
    filesError: Boolean,
    watchedKeys: Set<String>,
    watchProgress: WatchProgressUiState,
    onPlay: (MagnetPlaybackTarget?) -> Unit,
) {
    val isBatch = fileRows.size > 1
    val isEpisodeBatch = fileRows.count { it.episode != null } >= 2
    val totalBytes = fileRows.sumOf { it.sizeBytes }.takeIf { it > 0 } ?: magnet.exactLength
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(
                when {
                    isEpisodeBatch -> Res.string.magnet_section_episodes
                    isBatch -> Res.string.magnet_section_files
                    else -> Res.string.magnet_section_file
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (isLoadingFiles) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.magnet_loading_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (fileRows.isNotEmpty()) {
            fileRows.forEach { row ->
                val isEpisode = row.episode != null
                val isWatched = isEpisode &&
                    row.isWatched(metaId, metaType, watchedKeys, watchProgress)
                TorrentFileRow(
                    title = row.filename,
                    label = row.episodeRowLabel(),
                    sizeBytes = row.sizeBytes,
                    onClick = { onPlay(row.toPlaybackTarget(fileRows)) },
                    trailing = {
                        if (isEpisode) {
                            EpisodeWatchedToggle(
                                isWatched = isWatched,
                                progress = row.progressFraction(metaId, watchProgress),
                                onClick = {
                                    val season = row.playbackSeason
                                    val episode = row.episode
                                    if (season != null && episode != null) {
                                        toggleMagnetEpisodeWatched(
                                            metaId = metaId,
                                            metaType = metaType,
                                            title = title,
                                            season = season,
                                            episode = episode,
                                            isWatched = isWatched,
                                        )
                                    }
                                },
                            )
                        } else {
                            PlayGlyph()
                        }
                    },
                )
            }
        } else {
            TorrentFileRow(
                title = magnet.displayName ?: magnet.uri,
                label = null,
                sizeBytes = magnet.exactLength,
                onClick = { onPlay(null) },
                trailing = { PlayGlyph() },
            )
        }
        if (filesError) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.magnet_files_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        totalBytes?.takeIf { it > 0 }?.let { bytes ->
            Text(
                text = "${stringResource(Res.string.magnet_size)}: ${formatBytes(bytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (magnet.trackers.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.magnet_trackers, magnet.trackers.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${stringResource(Res.string.magnet_hash)}: ${magnet.infoHash.take(8)}…${magnet.infoHash.takeLast(8)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TorrentFileRow(
    title: String,
    label: String?,
    sizeBytes: Long?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            sizeBytes?.takeIf { it > 0 }?.let { bytes ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatBytes(bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        trailing()
    }
}

@Composable
private fun PlayGlyph() {
    Icon(
        imageVector = Icons.Filled.PlayArrow,
        contentDescription = stringResource(Res.string.magnet_play),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 7.dp)
            .size(18.dp),
    )
}

@Composable
private fun EpisodeWatchedToggle(
    isWatched: Boolean,
    progress: Float,
    onClick: () -> Unit,
) {
    val palette = MaterialTheme.themePalette
    val accent = palette.accentBrush()
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isWatched -> Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = stringResource(Res.string.episode_mark_unwatched),
                    tint = palette.onSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }

            progress > 0f -> Box(
                modifier = Modifier.size(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 2.dp.toPx()
                    drawCircle(color = trackColor, style = Stroke(width = stroke))
                    drawArc(
                        brush = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(Res.string.episode_mark_watched),
                    tint = mutedColor,
                    modifier = Modifier.size(15.dp),
                )
            }

            else -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(Res.string.episode_mark_watched),
                    tint = mutedColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun MagnetFileRow.episodeRowLabel(): String? {
    val episode = episode ?: return null
    val season = season
    return if (season != null) {
        "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    } else {
        "${stringResource(Res.string.magnet_episode)} $episode"
    }
}

@Composable
private fun FailureMessage(failure: MagnetResolveFailure) {
    val (title, body) = when (failure) {
        MagnetResolveFailure.MissingTmdbKey -> stringResource(Res.string.magnet_missing_key) to null
        MagnetResolveFailure.TmdbUnavailable -> stringResource(Res.string.magnet_unavailable) to null
        MagnetResolveFailure.NoMatch -> stringResource(Res.string.magnet_no_match_title) to stringResource(Res.string.magnet_no_match_body)
    }
    MagnetMessage(title = title, body = body)
}

@Composable
private fun MagnetMessage(title: String, body: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        body?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.magnet_search_another),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(Res.string.magnet_search_section_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.magnet_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(Res.string.magnet_search_action))
        }
    }
}

private fun formatRating(value: Double): String =
    "${(value * 10).roundToInt() / 10.0}"

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "${(bytes / 1_073_741_824.0 * 10).roundToInt() / 10.0} GB"
    bytes >= 1_048_576L -> "${(bytes / 1_048_576.0 * 10).roundToInt() / 10.0} MB"
    bytes >= 1_024L -> "${(bytes / 1_024.0 * 10).roundToInt() / 10.0} KB"
    else -> "$bytes B"
}

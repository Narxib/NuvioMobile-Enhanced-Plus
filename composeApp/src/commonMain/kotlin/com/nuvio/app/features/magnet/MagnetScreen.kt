package com.nuvio.app.features.magnet

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.features.p2p.P2pConsentDialog
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.navigation.LocalUseNativeNavigation
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.magnet_episode
import nuvio.composeapp.generated.resources.magnet_hash
import nuvio.composeapp.generated.resources.magnet_invalid_body
import nuvio.composeapp.generated.resources.magnet_invalid_title
import nuvio.composeapp.generated.resources.magnet_loading
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
import nuvio.composeapp.generated.resources.magnet_section_file
import nuvio.composeapp.generated.resources.magnet_series
import nuvio.composeapp.generated.resources.magnet_size
import nuvio.composeapp.generated.resources.magnet_title
import nuvio.composeapp.generated.resources.magnet_trackers
import nuvio.composeapp.generated.resources.magnet_unavailable
import nuvio.composeapp.generated.resources.magnet_view_details
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun MagnetScreen(
    url: String,
    onBack: () -> Unit,
    onPlay: (MagnetResolution) -> Unit,
    onOpenDetails: (MagnetMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val magnet = remember(url) { MagnetLink.parse(url) }
    var resolution by remember(url) { mutableStateOf<MagnetResolution?>(null) }
    var isLoading by remember(url) { mutableStateOf(magnet != null) }
    var searchQuery by remember(url) { mutableStateOf("") }
    var showSearch by remember(url) { mutableStateOf(false) }
    var showP2pConsent by remember { mutableStateOf(false) }
    val p2pSettings by remember {
        P2pSettingsRepository.ensureLoaded()
        P2pSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val background = MaterialTheme.colorScheme.background
    val match = resolution?.match

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

    fun play() {
        if (!P2pSettingsRepository.isVisible) return
        val current = resolution ?: return
        if (!p2pSettings.p2pEnabled) {
            showP2pConsent = true
            return
        }
        onPlay(current)
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
                        onPlay = ::play,
                        onOpenDetails = { currentResolution.match?.let(onOpenDetails) },
                        onSearchAnother = {
                            searchQuery = currentResolution.hint?.title.orEmpty()
                            showSearch = true
                        },
                    )
                }
            }

            if (!isLoading && magnet != null && (showSearch || match == null)) {
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
        P2pConsentDialog(
            onEnableP2p = {
                P2pSettingsRepository.setP2pEnabled(true)
                showP2pConsent = false
                resolution?.let(onPlay)
            },
            onDismiss = { showP2pConsent = false },
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
    onPlay: () -> Unit,
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
    val episodeLine = listOfNotNull(
        match?.episodeLabel?.let { label -> "${stringResource(Res.string.magnet_episode)} $label" },
        match?.episodeTitle,
    ).joinToString(" · ")

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
            match?.posterUrl?.let { poster ->
                NuvioAsyncImage(
                    imageUrl = poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
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
    TorrentDetails(magnet)

    Spacer(modifier = Modifier.height(20.dp))
    Button(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        enabled = P2pSettingsRepository.isVisible,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(Res.string.magnet_play))
    }

    if (match != null) {
        Spacer(modifier = Modifier.height(8.dp))
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

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = onSearchAnother) {
        Text(text = stringResource(Res.string.magnet_search_another))
    }
}

@Composable
private fun TorrentDetails(magnet: MagnetLink) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(Res.string.magnet_section_file),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = magnet.displayName ?: magnet.uri,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(10.dp))
        magnet.exactLength?.takeIf { it > 0 }?.let { bytes ->
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
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.magnet_search_hint)) },
            singleLine = true,
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

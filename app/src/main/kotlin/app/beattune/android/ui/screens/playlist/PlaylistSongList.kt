package app.beattune.android.ui.screens.playlist

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.beattune.android.Database
import app.beattune.android.LocalPlayerAwareWindowInsets
import app.beattune.android.LocalPlayerServiceBinder
import app.beattune.android.R
import app.beattune.android.models.Playlist
import app.beattune.android.models.SongPlaylistMap
import app.beattune.android.query
import app.beattune.android.transaction
import app.beattune.android.ui.components.LocalMenuState
import app.beattune.android.ui.components.ShimmerHost
import app.beattune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.beattune.android.ui.components.themed.Header
import app.beattune.android.ui.components.themed.HeaderIconButton
import app.beattune.android.ui.components.themed.HeaderPlaceholder
import app.beattune.android.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.beattune.android.ui.components.themed.NonQueuedMediaItemMenu
import app.beattune.android.ui.components.themed.PlaylistInfo
import app.beattune.android.ui.components.themed.SecondaryTextButton
import app.beattune.android.ui.components.themed.TextFieldDialog
import app.beattune.android.ui.components.themed.adaptiveThumbnailContent
import app.beattune.android.ui.items.SongItem
import app.beattune.android.ui.items.SongItemPlaceholder
import app.beattune.android.utils.PlaylistDownloadIcon
import app.beattune.android.utils.asMediaItem
import app.beattune.android.utils.completed
import app.beattune.android.utils.enqueue
import app.beattune.android.utils.forcePlayAtIndex
import app.beattune.android.utils.forcePlayFromBeginning
import app.beattune.android.utils.playingSong
import app.beattune.compose.persist.persist
import app.beattune.core.ui.Dimensions
import app.beattune.core.ui.LocalAppearance
import app.beattune.core.ui.utils.isLandscape
import app.beattune.providers.innertube.Innertube
import app.beattune.providers.innertube.models.bodies.BrowseBody
import app.beattune.providers.innertube.requests.playlistPage
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistSongList(
    browseId: String,
    params: String?,
    maxDepth: Int?,
    shouldDedup: Boolean,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    var playlistPage by persist<Innertube.PlaylistOrAlbumPage?>("playlist/$browseId/playlistPage")

    LaunchedEffect(Unit) {
        if (playlistPage != null && playlistPage?.songsPage?.continuation == null) return@LaunchedEffect

        playlistPage = withContext(Dispatchers.IO) {
            Innertube
                .playlistPage(BrowseBody(browseId = browseId, params = params))
                ?.completed(
                    maxDepth = maxDepth ?: Int.MAX_VALUE,
                    shouldDedup = shouldDedup
                )
                ?.getOrNull()
        }
    }

    var isImportingPlaylist by rememberSaveable { mutableStateOf(false) }

    if (isImportingPlaylist) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        initialTextInput = playlistPage?.title.orEmpty(),
        onDismiss = { isImportingPlaylist = false },
        onAccept = { text ->
            query {
                transaction {
                    val playlistId = Database.insert(
                        Playlist(
                            name = text,
                            browseId = browseId,
                            thumbnail = playlistPage?.thumbnail?.url
                        )
                    )

                    playlistPage?.songsPage?.items
                        ?.map(Innertube.SongItem::asMediaItem)
                        ?.onEach(Database::insert)
                        ?.mapIndexed { index, mediaItem ->
                            SongPlaylistMap(
                                songId = mediaItem.mediaId,
                                playlistId = playlistId,
                                position = index
                            )
                        }?.let(Database::insertSongPlaylistMaps)
                }
            }
        }
    )

    val headerContent: @Composable () -> Unit = {
        if (playlistPage == null) HeaderPlaceholder(modifier = Modifier.shimmer())
        else Header(title = playlistPage?.title ?: stringResource(R.string.unknown)) {
            SecondaryTextButton(
                text = stringResource(R.string.enqueue),
                enabled = playlistPage?.songsPage?.items?.isNotEmpty() == true,
                onClick = {
                    playlistPage?.songsPage?.items?.map(Innertube.SongItem::asMediaItem)
                        ?.let { mediaItems ->
                            binder?.player?.enqueue(mediaItems)
                        }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            playlistPage?.songsPage?.items?.map(Innertube.SongItem::asMediaItem)
                ?.let { PlaylistDownloadIcon(songs = it.toImmutableList()) }

            HeaderIconButton(
                icon = R.drawable.add,
                color = colorPalette.text,
                onClick = { isImportingPlaylist = true }
            )

            HeaderIconButton(
                icon = R.drawable.share_social,
                color = colorPalette.text,
                onClick = {
                    val url = playlistPage?.url
                        ?: "https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}"

                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }

                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            )
        }
    }

    val thumbnailContent = adaptiveThumbnailContent(
        isLoading = playlistPage == null,
        url = playlistPage?.thumbnail?.url
    )

    val lazyListState = rememberLazyListState()

    val (currentMediaId, playing) = playingSong(binder)

    LayoutWithAdaptiveThumbnail(
        thumbnailContent = thumbnailContent,
        modifier = modifier
    ) {
        Box {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette.background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        headerContent()
                        if (!isLandscape) thumbnailContent()
                        PlaylistInfo(playlist = playlistPage)
                    }
                }

                itemsIndexed(items = playlistPage?.songsPage?.items ?: emptyList()) { index, song ->
                    SongItem(
                        song = song,
                        thumbnailSize = Dimensions.thumbnails.song,
                        modifier = Modifier
                            .combinedClickable(
                                onLongClick = {
                                    menuState.display {
                                        NonQueuedMediaItemMenu(
                                            onDismiss = menuState::hide,
                                            mediaItem = song.asMediaItem
                                        )
                                    }
                                },
                                onClick = {
                                    playlistPage?.songsPage?.items?.map(Innertube.SongItem::asMediaItem)
                                        ?.let { mediaItems ->
                                            binder?.stopRadio()
                                            binder?.player?.forcePlayAtIndex(mediaItems, index)
                                        }
                                }
                            ),
                        isPlaying = playing && currentMediaId == song.key
                    )
                }

                if (playlistPage == null) item(key = "loading") {
                    ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                        repeat(4) {
                            SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                        }
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                onClick = {
                    playlistPage?.songsPage?.items?.let { songs ->
                        if (songs.isNotEmpty()) {
                            binder?.stopRadio()
                            binder?.player?.forcePlayFromBeginning(
                                songs.shuffled().map(Innertube.SongItem::asMediaItem)
                            )
                        }
                    }
                }
            )
        }
    }
}

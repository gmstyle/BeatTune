package app.beattune.android.ui.screens.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.beattune.android.LocalPlayerAwareWindowInsets
import app.beattune.android.LocalPlayerServiceBinder
import app.beattune.android.R
import app.beattune.android.models.Song
import app.beattune.android.ui.components.LocalMenuState
import app.beattune.android.ui.components.ShimmerHost
import app.beattune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.beattune.android.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.beattune.android.ui.components.themed.NonQueuedMediaItemMenu
import app.beattune.android.ui.components.themed.SecondaryTextButton
import app.beattune.android.ui.items.SongItem
import app.beattune.android.ui.items.SongItemPlaceholder
import app.beattune.android.utils.PlaylistDownloadIcon
import app.beattune.android.utils.asMediaItem
import app.beattune.android.utils.enqueue
import app.beattune.android.utils.forcePlayAtIndex
import app.beattune.android.utils.forcePlayFromBeginning
import app.beattune.android.utils.playingSong
import app.beattune.core.ui.Dimensions
import app.beattune.core.ui.LocalAppearance
import app.beattune.core.ui.utils.isLandscape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// TODO: migrate to single impl for all 'song lists'
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumSongs(
    songs: ImmutableList<Song>,
    headerContent: @Composable (
        beforeContent: (@Composable () -> Unit)?,
        afterContent: (@Composable () -> Unit)?
    ) -> Unit,
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    afterHeaderContent: (@Composable () -> Unit)? = null
) = LayoutWithAdaptiveThumbnail(
    thumbnailContent = thumbnailContent,
    modifier = modifier
) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val lazyListState = rememberLazyListState()

    val (currentMediaId, playing) = playingSong(binder)

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
                    headerContent(
                        {
                            SecondaryTextButton(
                                text = stringResource(R.string.enqueue),
                                enabled = songs.isNotEmpty(),
                                onClick = {
                                    binder?.player?.enqueue(songs.map(Song::asMediaItem))
                                }
                            )
                        },
                        {
                            PlaylistDownloadIcon(
                                songs = songs.map(Song::asMediaItem).toImmutableList()
                            )
                        }
                    )

                    if (!isLandscape) thumbnailContent()
                    afterHeaderContent?.invoke()
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.id }
            ) { index, song ->
                SongItem(
                    song = song,
                    index = index,
                    thumbnailSize = Dimensions.thumbnails.song,
                    modifier = Modifier.combinedClickable(
                        onLongClick = {
                            menuState.display {
                                NonQueuedMediaItemMenu(
                                    onDismiss = menuState::hide,
                                    mediaItem = song.asMediaItem
                                )
                            }
                        },
                        onClick = {
                            binder?.stopRadio()
                            binder?.player?.forcePlayAtIndex(
                                items = songs.map(Song::asMediaItem),
                                index = index
                            )
                        }
                    ),
                    isPlaying = playing && currentMediaId == song.id
                )
            }

            if (songs.isEmpty()) item(key = "loading") {
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
                if (songs.isNotEmpty()) {
                    binder?.stopRadio()
                    binder?.player?.forcePlayFromBeginning(
                        songs.shuffled().map(Song::asMediaItem)
                    )
                }
            }
        )
    }
}

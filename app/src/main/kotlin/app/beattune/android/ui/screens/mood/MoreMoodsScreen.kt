package app.beattune.android.ui.screens.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.beattune.android.R
import app.beattune.android.models.toUiMood
import app.beattune.android.ui.components.themed.Scaffold
import app.beattune.android.ui.screens.GlobalRoutes
import app.beattune.android.ui.screens.Route
import app.beattune.android.ui.screens.moodRoute
import app.beattune.compose.persist.PersistMapCleanup
import app.beattune.compose.routing.RouteHandler

@Route
@Composable
fun MoreMoodsScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup(prefix = "more_moods/")

    RouteHandler {
        GlobalRoutes()

        moodRoute { mood ->
            MoodScreen(mood = mood)
        }

        Content {
            Scaffold(
                key = "moremoods",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChange = { },
                tabColumnContent = {
                    tab(0, R.string.moods_and_genres, R.drawable.playlist)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> MoreMoodsList(
                            onMoodClick = { mood -> moodRoute(mood.toUiMood()) }
                        )
                    }
                }
            }
        }
    }
}

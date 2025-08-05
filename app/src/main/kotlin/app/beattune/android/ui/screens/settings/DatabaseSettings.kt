package app.beattune.android.ui.screens.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.beattune.android.Database
import app.beattune.android.R
import app.beattune.android.internal
import app.beattune.android.path
import app.beattune.android.preferences.DataPreferences
import app.beattune.android.query
import app.beattune.android.service.PlayerService
import app.beattune.android.transaction
import app.beattune.android.ui.screens.Route
import app.beattune.android.utils.intent
import app.beattune.android.utils.toast
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

@Route
@Composable
fun DatabaseSettings() = with(DataPreferences) {
    val context = LocalContext.current

    val eventsCount by remember { Database.instance.eventsCount().distinctUntilChanged() }
        .collectAsState(initial = 0)

    val blacklistLength by remember { Database.instance.blacklistLength().distinctUntilChanged() }
        .collectAsState(initial = 0)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType = "application/vnd.sqlite3")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        query {
            Database.instance.checkpoint()

            context.applicationContext.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(Database.instance.internal.path).use { input -> input.copyTo(output) }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        query {
            Database.instance.checkpoint()
            Database.instance.internal.close()

            context.applicationContext.contentResolver.openInputStream(uri)
                ?.use { inputStream ->
                    FileOutputStream(Database.instance.internal.path).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

            context.stopService(context.intent<PlayerService>())
            exitProcess(0)
        }
    }

    SettingsCategoryScreen(title = stringResource(R.string.database)) {
        SettingsGroup(title = stringResource(R.string.cleanup)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.pause_playback_history),
                text = stringResource(R.string.pause_playback_history_description),
                isChecked = pauseHistory,
                onCheckedChange = { pauseHistory = !pauseHistory }
            )

            AnimatedVisibility(visible = pauseHistory) {
                SettingsDescription(
                    text = stringResource(R.string.pause_playback_history_warning),
                    important = true
                )
            }

            AnimatedVisibility(visible = !(pauseHistory && eventsCount == 0)) {
                SettingsEntry(
                    title = stringResource(R.string.reset_quick_picks),
                    text = if (eventsCount > 0) pluralStringResource(
                        R.plurals.format_reset_quick_picks_amount,
                        eventsCount,
                        eventsCount
                    )
                    else stringResource(R.string.quick_picks_empty),
                    onClick = { query(Database.instance::clearEvents) },
                    isEnabled = eventsCount > 0
                )
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.pause_playback_time),
                text = stringResource(
                    R.string.format_pause_playback_time_description,
                    topListLength
                ),
                isChecked = pausePlaytime,
                onCheckedChange = { pausePlaytime = !pausePlaytime }
            )

            SettingsEntry(
                title = stringResource(R.string.reset_blacklist),
                text = if (blacklistLength > 0) pluralStringResource(
                    R.plurals.format_reset_blacklist_description,
                    blacklistLength,
                    blacklistLength
                ) else stringResource(R.string.blacklist_empty),
                isEnabled = blacklistLength > 0,
                onClick = {
                    transaction {
                        Database.instance.resetBlacklist()
                    }
                }
            )
        }
        SettingsGroup(
            title = stringResource(R.string.backup),
            description = stringResource(R.string.backup_description)
        ) {
            SettingsEntry(
                title = stringResource(R.string.backup),
                text = stringResource(R.string.backup_action_description),
                onClick = {
                    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

                    try {
                        backupLauncher.launch("BeatTune_backup_${dateFormat.format(Date())}.db")
                    } catch (e: ActivityNotFoundException) {
                        context.toast(context.getString(R.string.no_file_chooser_installed))
                    }
                }
            )
        }
        SettingsGroup(
            title = stringResource(R.string.restore),
            description = stringResource(R.string.restore_warning),
            important = true
        ) {
            SettingsEntry(
                title = stringResource(R.string.restore),
                text = stringResource(R.string.restore_description),
                onClick = {
                    try {
                        restoreLauncher.launch(
                            arrayOf(
                                "application/vnd.sqlite3",
                                "application/x-sqlite3",
                                "application/octet-stream"
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        context.toast(context.getString(R.string.no_file_chooser_installed))
                    }
                }
            )
        }
    }
}

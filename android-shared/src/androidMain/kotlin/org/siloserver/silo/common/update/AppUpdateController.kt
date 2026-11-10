package org.siloserver.silo.common.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Settings update row shows. */
sealed interface AppUpdateUi {
    data object Idle : AppUpdateUi
    data object Checking : AppUpdateUi
    data object UpToDate : AppUpdateUi
    data class Available(val update: AppUpdateInfo) : AppUpdateUi
    data class Downloading(val progress: Float) : AppUpdateUi
    data object Installing : AppUpdateUi
    data class Error(val message: String) : AppUpdateUi
}

/** Drives check, download and install for a Settings screen; one per screen. */
class AppUpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val variant: String,
) {
    private val _state = MutableStateFlow<AppUpdateUi>(AppUpdateUi.Idle)
    val state: StateFlow<AppUpdateUi> = _state.asStateFlow()

    fun check(serverUrl: String) {
        if (_state.value is AppUpdateUi.Checking || _state.value is AppUpdateUi.Downloading) return
        _state.value = AppUpdateUi.Checking
        scope.launch {
            _state.value = when (val result = AppUpdater.check(context, serverUrl, variant)) {
                is AppUpdateCheck.Available -> AppUpdateUi.Available(result.update)
                AppUpdateCheck.UpToDate -> AppUpdateUi.UpToDate
                is AppUpdateCheck.Failed -> AppUpdateUi.Error(result.reason)
            }
        }
    }

    fun downloadAndInstall() {
        val update = (_state.value as? AppUpdateUi.Available)?.update ?: return
        _state.value = AppUpdateUi.Downloading(0f)
        scope.launch {
            val file = AppUpdater.download(context, update) { _state.value = AppUpdateUi.Downloading(it) }
                .getOrElse {
                    _state.value = AppUpdateUi.Error(it.message ?: "Download failed")
                    return@launch
                }
            _state.value = AppUpdateUi.Installing
            val error = AppUpdater.install(context, file)
            _state.value = if (error == null) AppUpdateUi.Installing else AppUpdateUi.Error(error)
        }
    }
}

/** The row's one-line summary. */
fun AppUpdateUi.summary(): String = when (this) {
    AppUpdateUi.Idle -> "Tap to check"
    AppUpdateUi.Checking -> "Checking…"
    AppUpdateUi.UpToDate -> "You're up to date"
    is AppUpdateUi.Available -> "Version ${update.versionName} is available"
    is AppUpdateUi.Downloading -> "Downloading ${(progress * 100).toInt()}%"
    AppUpdateUi.Installing -> "Waiting for the installer…"
    is AppUpdateUi.Error -> message
}

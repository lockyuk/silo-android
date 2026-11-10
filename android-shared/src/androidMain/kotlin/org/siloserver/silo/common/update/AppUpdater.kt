package org.siloserver.silo.common.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

/** An APK the update host offers that is newer than the installed app. */
data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String?,
)

/** What a check found. */
sealed interface AppUpdateCheck {
    data class Available(val update: AppUpdateInfo) : AppUpdateCheck
    data object UpToDate : AppUpdateCheck
    data class Failed(val reason: String) : AppUpdateCheck
}

/**
 * Self-update for sideloaded builds. The user's Silo host serves
 * `<server>/app-updates/latest.json` (written by the publish script) next to
 * the signed APKs. Android only installs an APK over the running app when the
 * signing key and package match and the version code is higher, so a bad or
 * foreign file cannot replace the app; the hash check guards against a
 * truncated download.
 *
 * latest.json:
 * `{"phone":{"versionCode":12,"versionName":"1.0.0","notes":"…","apks":{"arm64-v8a":{"file":"x.apk","sha256":"…","size":123}}},"tv":{…}}`
 */
object AppUpdater {
    const val PhoneVariant = "phone"
    const val TvVariant = "tv"

    private const val UpdatePath = "app-updates/"
    private const val ConnectTimeoutMs = 8_000
    private const val ReadTimeoutMs = 20_000

    @Suppress("DEPRECATION")
    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    suspend fun check(context: Context, serverUrl: String, variant: String): AppUpdateCheck =
        withContext(Dispatchers.IO) {
            val base = baseUrl(serverUrl) ?: return@withContext AppUpdateCheck.Failed("No server address")
            runCatching {
                val json = JSONObject(httpGetText(base + "latest.json"))
                val entry = json.optJSONObject(variant)
                    ?: return@runCatching AppUpdateCheck.UpToDate
                val code = entry.getLong("versionCode")
                if (code <= installedVersionCode(context)) return@runCatching AppUpdateCheck.UpToDate
                val apks = entry.getJSONObject("apks")
                val abi = Build.SUPPORTED_ABIS.firstOrNull { apks.has(it) }
                    ?: apks.keys().asSequence().firstOrNull()
                    ?: return@runCatching AppUpdateCheck.Failed("No APK is published for this device")
                val apk = apks.getJSONObject(abi)
                AppUpdateCheck.Available(
                    AppUpdateInfo(
                        versionCode = code,
                        versionName = entry.optString("versionName", code.toString()),
                        apkUrl = base + apk.getString("file"),
                        sha256 = apk.getString("sha256").lowercase(),
                        sizeBytes = apk.optLong("size", -1L),
                        notes = entry.optString("notes").takeIf { it.isNotBlank() },
                    ),
                )
            }.getOrElse { AppUpdateCheck.Failed(it.message ?: "Could not reach the update host") }
        }

    /** Downloads and verifies the APK; returns the file. [onProgress] gets 0f..1f. */
    suspend fun download(
        context: Context,
        update: AppUpdateInfo,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "app-update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "update-${update.versionCode}.apk")
            val connection = open(update.apkUrl)
            try {
                check(connection.responseCode == 200) { "Download failed (HTTP ${connection.responseCode})" }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(actual == update.sha256) { "The downloaded file is damaged. Try again." }
            } finally {
                connection.disconnect()
            }
            target
        }
    }

    /**
     * Hands the verified APK to the system installer. Android asks the user to
     * confirm (and, the first time, to allow installs from Silo); the app
     * restarts itself on the new version. Returns an error message, or null if
     * the installer took over.
     */
    suspend fun install(context: Context, apk: File): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("silo.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val action = "${app.packageName}.APP_UPDATE_RESULT"
                val result = suspendCancellableCoroutine { continuation ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context, intent: Intent) {
                            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                    // The confirmation screen; the result broadcast follows it.
                                    @Suppress("DEPRECATION")
                                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { c.startActivity(confirm) }
                                    if (continuation.isActive) continuation.resume(null)
                                }
                                PackageInstaller.STATUS_SUCCESS -> if (continuation.isActive) continuation.resume(null)
                                else -> if (continuation.isActive) {
                                    continuation.resume(
                                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                            ?: "The installer refused the update",
                                    )
                                }
                            }
                        }
                    }
                    ContextCompat.registerReceiver(
                        app,
                        receiver,
                        IntentFilter(action),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                    continuation.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(
                        app, sessionId, Intent(action).setPackage(app.packageName), flags,
                    )
                    session.commit(pending.intentSender)
                }
                result
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            e.message ?: "Could not start the install"
        }
    }

    private fun baseUrl(serverUrl: String): String? {
        val trimmed = serverUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        return "$trimmed/$UpdatePath"
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = ConnectTimeoutMs
            readTimeout = ReadTimeoutMs
            instanceFollowRedirects = true
        }

    private fun httpGetText(url: String): String {
        val connection = open(url)
        try {
            check(connection.responseCode == 200) { "Update host answered HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

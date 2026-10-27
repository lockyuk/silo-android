package org.siloserver.silo.android.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

/**
 * Runs a rich notification's background buttons (mark read, mute show) as the
 * login the push was issued for. The work is short and bounded by the push
 * budget, so it runs under [goAsync] rather than a worker.
 */
class PushActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val deliveryId = intent.getStringExtra(PushNotificationPresenter.EXTRA_DELIVERY_ID) ?: return
        val issuer = PushIssuer(
            intent.getStringExtra(EXTRA_ISSUER_SERVER_ID).orEmpty(),
            intent.getStringExtra(EXTRA_ISSUER_PROFILE_ID).orEmpty(),
        )
        if (issuer.serverId.isBlank() || issuer.profileId.isBlank()) return
        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                get<PushNotificationPresenter>(PushNotificationPresenter::class.java)
                    .performAction(action, deliveryId, seriesId, issuer)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "silo_notification_action"
        const val EXTRA_SERIES_ID = "silo_notification_series_id"
        const val EXTRA_ISSUER_SERVER_ID = "silo_notification_issuer_server_id"
        const val EXTRA_ISSUER_PROFILE_ID = "silo_notification_issuer_profile_id"
    }
}

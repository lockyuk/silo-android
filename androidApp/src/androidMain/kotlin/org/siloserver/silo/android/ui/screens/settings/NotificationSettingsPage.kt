package org.siloserver.silo.android.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.android.ui.theme.SiloNavPillBorder
import org.siloserver.silo.android.ui.theme.SiloNavPillContent
import org.siloserver.silo.android.ui.theme.SiloNavPillSelected
import org.siloserver.silo.android.ui.theme.SiloNavPillSelectedContent
import org.siloserver.silo.android.ui.theme.SiloNavPillSurface
import org.siloserver.silo.android.ui.theme.SiloOnSurface
import org.siloserver.silo.android.ui.theme.SiloSecondaryText
import org.siloserver.silo.model.personal.UserLibrary

private val WEEKDAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val ActiveGreen = Color(0xFF34C759)

/**
 * Notifications, matching the web settings page: which systems the server
 * runs, what to be told about, then email (address, instant / daily / weekly
 * in any mix, libraries, what to send) and push (on or off, libraries).
 */
@Composable
fun SettingsNotificationsScreen(viewModel: SettingsViewModel, onBackClick: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    SettingsPageScaffold(title = "Notifications", onBackClick = onBackClick) {
        item(key = "status") {
            SettingsSection(
                title = null,
                footer = if (!state.emailActive || !state.pushActive) {
                    "Inactive systems are switched off by the server administrator."
                } else {
                    null
                },
            ) {
                StatusRow("Email", state.emailActive)
                StatusRow("Push", state.pushActive)
            }
        }
        item(key = "in-app") {
            SettingsSection(
                title = null,
                footer = "Show alerts inside Silo as new releases arrive.",
            ) {
                SettingsSwitchRow(
                    label = "In-App Notifications",
                    checked = state.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )
            }
        }
        if (state.notificationsEnabled) {
            item(key = "topics") {
                SettingsSection(
                    title = "Notify Me About",
                    footer = "Favorites and Next Up cover new episodes of series you follow. Watchlist covers " +
                        "titles that become available. Continue Watching covers titles you started but " +
                        "haven't finished.",
                ) {
                    SettingsSwitchRow(
                        label = "Favorites",
                        checked = state.notifyFavorites,
                        onCheckedChange = viewModel::setNotifyFavorites,
                    )
                    SettingsSwitchRow(
                        label = "Watchlist",
                        checked = state.notifyWatchlist,
                        onCheckedChange = viewModel::setNotifyWatchlist,
                    )
                    SettingsSwitchRow(
                        label = "Continue Watching",
                        checked = state.notifyContinueWatching,
                        onCheckedChange = viewModel::setNotifyContinueWatching,
                    )
                    SettingsSwitchRow(
                        label = "Next Up",
                        checked = state.notifyNextUp,
                        onCheckedChange = viewModel::setNotifyNextUp,
                    )
                }
            }
            item(key = "everything-new") {
                SettingsSection(title = "Everything New") {
                    SettingsSwitchRow(
                        label = "Every new episode",
                        checked = state.notifyNewEpisodes,
                        onCheckedChange = viewModel::setNotifyNewEpisodes,
                    )
                    SettingsSwitchRow(
                        label = "Every new movie",
                        checked = state.notifyNewMovies,
                        onCheckedChange = viewModel::setNotifyNewMovies,
                    )
                }
            }
        }
        val email = state.email
        if (state.emailActive && email != null) {
            item(key = "email") {
                EmailSection(state = state, viewModel = viewModel)
            }
        }
        if (state.pushActive && state.notificationsAvailable) {
            item(key = "push") {
                SettingsSection(
                    title = "Push Notifications",
                    footer = "Instant alerts on the phones where you've signed in to Silo.",
                ) {
                    PillGroup(
                        title = "Instant notifications",
                        description = if (state.pushEnabled) {
                            "A push shortly after something new arrives"
                        } else {
                            "Off: notifications stay in your inbox"
                        },
                    ) {
                        Pill("On", state.pushEnabled) { viewModel.setPushEnabled(true) }
                        Pill("Off", !state.pushEnabled) { viewModel.setPushEnabled(false) }
                    }
                    if (state.pushEnabled) {
                        LibraryPills(state.notificationLibraries, state.pushLibraryIds, viewModel::setPushLibraries)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val email = state.email ?: return
    var editing by remember { mutableStateOf(false) }
    val hasAddress = email.customEmail.isNotBlank()
    val hour = state.emailDigestHour.toString().padStart(2, '0')
    val weekday = WEEKDAYS.getOrElse(state.emailDigestWeekday) { "Sunday" }
    val summary = listOfNotNull(
        "an email shortly after something new arrives".takeIf { email.instant },
        "a daily digest around $hour:00".takeIf { email.daily },
        "a weekly digest on $weekday around $hour:00".takeIf { email.weekly },
    ).joinToString(", ").replaceFirstChar { it.uppercase() }

    SettingsSection(
        title = "Email Notifications",
        footer = state.emailMessage
            ?: email.pendingEmail.takeIf { it.isNotBlank() }?.let { "Waiting for $it to be confirmed." },
    ) {
        SettingsRow(
            label = "Deliver to",
            description = if (hasAddress) email.customEmail else "No address yet: add one to receive emails",
            enabled = email.canEditAddress,
            onClick = if (email.canEditAddress) ({ editing = true }) else null,
            trailing = {
                if (email.canEditAddress) {
                    if (hasAddress) {
                        TextButton(onClick = viewModel::clearEmailAddress) { Text("Remove") }
                    }
                    TextButton(onClick = { editing = true }) { Text(if (hasAddress) "Change" else "Add") }
                }
            },
        )
        PillGroup(
            title = "Send",
            description = when {
                !hasAddress -> "Add and confirm an address to choose what to receive"
                summary.isEmpty() -> "Nothing: email is off for this profile"
                else -> summary
            },
        ) {
            Pill("Instant", email.instant, enabled = hasAddress && (state.emailAllowInstant || email.instant)) {
                viewModel.setEmailInstant(!email.instant)
            }
            Pill("Daily digest", email.daily, enabled = hasAddress) { viewModel.setEmailDaily(!email.daily) }
            Pill("Weekly digest", email.weekly, enabled = hasAddress) { viewModel.setEmailWeekly(!email.weekly) }
        }
        if (email.instant || email.daily || email.weekly) {
            LibraryPills(state.notificationLibraries, email.libraryIds, viewModel::setEmailLibraries)
            PillGroup(title = "What to send") {
                Pill("Shows I follow", email.content != "all") { viewModel.setEmailContent("followed") }
                Pill("Everything new", email.content == "all") { viewModel.setEmailContent("all") }
            }
        }
    }
    if (editing) {
        AddressDialog(
            current = email.customEmail,
            onDismiss = { editing = false },
            onSubmit = {
                editing = false
                viewModel.requestEmailAddress(it)
            },
        )
    }
}

@Composable
private fun AddressDialog(current: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var address by remember { mutableStateOf(current) }
    val valid = address.trim().let { it.contains('@') && it.substringAfter('@').contains('.') }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Email address") },
        text = {
            Column {
                Text(
                    "We'll email a link to confirm it. The address applies once you open the link.",
                    color = SiloSecondaryText,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSubmit(address) }) { Text("Send link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatusRow(label: String, active: Boolean) {
    SettingsRow(
        label = label,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (active) ActiveGreen else SiloSecondaryText, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active) "Active" else "Inactive",
                    color = if (active) ActiveGreen else SiloSecondaryText,
                    fontSize = 14.sp,
                )
            }
        },
    )
}

/** A labelled row of pills inside a settings card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillGroup(title: String, description: String? = null, pills: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = SiloOnSurface, fontSize = 15.sp)
        if (description != null) {
            Text(description, color = SiloSecondaryText, fontSize = 13.sp)
        }
        Spacer(Modifier.size(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pills()
        }
    }
}

@Composable
private fun Pill(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (on) SiloNavPillSelected else SiloNavPillSurface,
        contentColor = if (on) SiloNavPillSelectedContent else SiloNavPillContent,
        border = if (on) null else BorderStroke(1.dp, SiloNavPillBorder),
        modifier = Modifier.then(if (enabled) Modifier else Modifier.background(Color.Transparent)),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = (if (on) SiloNavPillSelectedContent else SiloNavPillContent).copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/**
 * Library pills: none picked means every library. Picking narrows it; "All
 * libraries" clears the choice.
 */
@Composable
private fun LibraryPills(libraries: List<UserLibrary>, selected: List<Int>, onChange: (List<Int>) -> Unit) {
    if (libraries.isEmpty()) return
    PillGroup(title = "Libraries", description = "Only new items from these libraries") {
        Pill("All libraries", selected.isEmpty()) { onChange(emptyList()) }
        libraries.forEach { library ->
            val on = library.id in selected
            Pill(library.name, on) { onChange(if (on) selected - library.id else selected + library.id) }
        }
    }
}

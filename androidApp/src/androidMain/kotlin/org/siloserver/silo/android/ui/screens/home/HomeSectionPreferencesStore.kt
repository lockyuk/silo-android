package org.siloserver.silo.android.ui.screens.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.network.ServerRegistry

/**
 * Device-local Home row order and visibility, scoped to the active server and
 * profile. The server remains authoritative for row membership and content;
 * unknown rows append in server order and start visible.
 */
class HomeSectionPreferencesStore(
    context: Context,
    private val serverRegistry: ServerRegistry,
) {
    @Serializable
    private data class StoredLayout(
        val orderedSectionIds: List<String> = emptyList(),
        val hiddenSectionIds: Set<String> = emptySet(),
        /** The row shown as the hero; null means Continue Watching. */
        val heroSectionId: String? = null,
        val heroDisabled: Boolean = false,
    )

    private val preferences = context.getSharedPreferences("home_section_preferences", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun isVisible(sectionId: String, profileId: String? = null): Boolean =
        sectionId !in load(profileId).hiddenSectionIds

    fun setVisible(sectionId: String, visible: Boolean, profileId: String? = null) {
        val current = load(profileId)
        val hidden = current.hiddenSectionIds.toMutableSet()
        if (visible) hidden.remove(sectionId) else hidden.add(sectionId)
        save(current.copy(hiddenSectionIds = hidden), profileId)
    }

    /**
     * Replaces the order of currently-known rows while retaining remembered
     * identities that are temporarily empty or unavailable.
     */
    fun setOrder(sectionIds: List<String>, profileId: String? = null) {
        val current = load(profileId)
        val visibleOrder = sectionIds.distinct()
        val visibleSet = visibleOrder.toSet()
        save(
            current.copy(
                orderedSectionIds = visibleOrder + current.orderedSectionIds.filterNot(visibleSet::contains),
            ),
            profileId,
        )
    }

    /** What Home shows as its hero, Continue Watching unless the profile chose otherwise. */
    fun heroChoice(profileId: String? = null): HeroChoice {
        val layout = load(profileId)
        return when {
            layout.heroDisabled -> HeroChoice.Off
            layout.heroSectionId != null -> HeroChoice.Section(layout.heroSectionId)
            else -> HeroChoice.ContinueWatching
        }
    }

    fun setHeroChoice(choice: HeroChoice, profileId: String? = null) {
        val current = load(profileId)
        save(
            current.copy(
                heroSectionId = (choice as? HeroChoice.Section)?.sectionId,
                heroDisabled = choice == HeroChoice.Off,
            ),
            profileId,
        )
    }

    /**
     * The row Home shows as its hero: the chosen row while it has items, else
     * Continue Watching. A row can be the hero even while it is hidden as a row.
     */
    fun heroSection(sections: List<ResolvedSection>, profileId: String? = null): ResolvedSection? {
        val populated = arrangedSections(sections, includingHidden = true, profileId = profileId)
        val continueWatching = populated.firstOrNull { it.isContinueWatchingHeroRow() }
        return when (val choice = heroChoice(profileId)) {
            HeroChoice.Off -> null
            HeroChoice.ContinueWatching -> continueWatching
            is HeroChoice.Section -> populated.firstOrNull { it.id == choice.sectionId } ?: continueWatching
        }
    }

    fun arrangedSections(
        sections: List<ResolvedSection>,
        includingHidden: Boolean = false,
        profileId: String? = null,
    ): List<ResolvedSection> {
        val layout = load(profileId)
        val rank = layout.orderedSectionIds.withIndex().associate { it.value to it.index }
        val arranged = sections
            .filter { it.items.isNotEmpty() }
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<ResolvedSection>>(
                    { rank[it.value.id] ?: Int.MAX_VALUE },
                    { it.index },
                ),
            )
            .map { it.value }
        return if (includingHidden) arranged else arranged.filterNot { it.id in layout.hiddenSectionIds }
    }

    private fun storageKey(profileIdOverride: String? = null): String? {
        val serverId = serverRegistry.activeServerId.value ?: "default"
        val profileId = profileIdOverride
            ?.takeIf { it.isNotBlank() }
            ?: serverRegistry.activeEntry.value?.profileId
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return "android.homeSections.v1.$serverId.$profileId"
    }

    private fun load(profileId: String? = null): StoredLayout {
        val key = storageKey(profileId) ?: return StoredLayout()
        val raw = preferences.getString(key, null) ?: return StoredLayout()
        return runCatching { json.decodeFromString<StoredLayout>(raw) }.getOrDefault(StoredLayout())
    }

    private fun save(layout: StoredLayout, profileId: String? = null) {
        val key = storageKey(profileId) ?: return
        preferences.edit().putString(key, json.encodeToString(layout)).apply()
        _revision.value += 1
    }
}

sealed interface HeroChoice {
    data object Off : HeroChoice
    data object ContinueWatching : HeroChoice
    data class Section(val sectionId: String) : HeroChoice
}

internal fun ResolvedSection.isContinueWatchingHeroRow(): Boolean =
    sectionType == "continue_watching" || sectionType == "in_progress"

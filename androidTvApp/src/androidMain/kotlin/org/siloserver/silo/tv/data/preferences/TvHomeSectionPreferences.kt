package org.siloserver.silo.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.tv.ui.components.isTvContinueWatchingRow

/** Device-local Home row order and visibility for one server/profile. */
data class TvHomeSectionLayout(
    val orderedSectionIds: List<String> = emptyList(),
    val hiddenSectionIds: Set<String> = emptySet(),
    /** The row shown as Home's hero; null means Continue Watching. */
    val heroSectionId: String? = null,
    val heroDisabled: Boolean = false,
    /** Which titles the hero shows from its row. */
    val heroContent: TvHeroContent = TvHeroContent.All,
)

/** What the hero shows: everything, only TV episodes/series, or only films. */
enum class TvHeroContent(val label: String) {
    All("TV & Movies"),
    Series("TV Shows"),
    Movies("Movies"),
    ;

    fun next(): TvHeroContent = entries[(ordinal + 1) % entries.size]

    fun accepts(type: String): Boolean = when (this) {
        All -> true
        Series -> type.lowercase() in setOf("episode", "series", "season", "show")
        Movies -> type.equals("movie", ignoreCase = true)
    }
}

data class TvHomeSectionPreferenceState(
    val authority: String? = null,
    val layout: TvHomeSectionLayout = TvHomeSectionLayout(),
    /** Changes only when the active authority or its saved layout changes. */
    val layoutRevision: Long = 0,
)

/**
 * Android TV counterpart to tvOS `HomeSectionPreferences`.
 *
 * The server remains authoritative for which populated rows exist. This store
 * only projects those rows into a device-local order/visibility layout, scoped
 * to the active server and profile. Newly-added server rows therefore append
 * in server order and remain visible until explicitly hidden.
 */
class TvHomeSectionPreferences(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val dataStoreFactory: (profileId: String) -> DataStore<Preferences> = { profileId ->
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(fileNameFor(profileId)) },
        )
    },
) {
    private data class Authority(val profileId: String, val serverId: String) {
        val identity: String = "$serverId\u001f$profileId"
    }

    private val mutex = Mutex()
    private val storeCache = mutableMapOf<String, DataStore<Preferences>>()
    private val _state = MutableStateFlow(TvHomeSectionPreferenceState())
    val state: StateFlow<TvHomeSectionPreferenceState> = _state.asStateFlow()

    private fun storeFor(profileId: String): DataStore<Preferences> =
        synchronized(storeCache) {
            storeCache.getOrPut(profileId) { dataStoreFactory(profileId) }
        }

    /** Load the active server/profile layout, if it is not already current. */
    suspend fun refresh() {
        val authority = activeAuthority()
        mutex.withLock {
            if (authority == null) {
                publishIfChanged(authority = null, layout = TvHomeSectionLayout())
                return
            }
            if (_state.value.authority == authority.identity) return
            publishIfChanged(authority.identity, readLayout(authority))
        }
    }

    suspend fun setVisible(sectionId: String, visible: Boolean) {
        val authority = activeAuthority() ?: return
        mutex.withLock {
            val current = currentLayout(authority)
            val wasVisible = sectionId !in current.hiddenSectionIds
            if (wasVisible == visible) return

            val hidden = current.hiddenSectionIds.toMutableSet().apply {
                if (visible) remove(sectionId) else add(sectionId)
            }
            val updated = current.copy(hiddenSectionIds = hidden)
            persist(authority, updated)
            publish(authority.identity, updated)
        }
    }

    /**
     * Replace the order of currently-known rows while retaining remembered
     * rows that are temporarily absent (for example empty Continue Watching).
     */
    suspend fun setOrder(sectionIds: List<String>) {
        val authority = activeAuthority() ?: return
        mutex.withLock {
            val current = currentLayout(authority)
            val updatedOrder = retainedOrder(sectionIds, current.orderedSectionIds)
            if (updatedOrder == current.orderedSectionIds) return

            val updated = current.copy(orderedSectionIds = updatedOrder)
            persist(authority, updated)
            publish(authority.identity, updated)
        }
    }

    /** Show [sectionId] as the hero, Continue Watching for null, or no hero when [disabled]. */
    suspend fun setHero(sectionId: String?, disabled: Boolean = false) {
        val authority = activeAuthority() ?: return
        mutex.withLock {
            val current = currentLayout(authority)
            val updated = current.copy(
                heroSectionId = sectionId.takeUnless { disabled },
                heroDisabled = disabled,
            )
            if (updated == current) return
            persist(authority, updated)
            publish(authority.identity, updated)
        }
    }

    /** Limit the hero to TV, films, or both. */
    suspend fun setHeroContent(content: TvHeroContent) {
        val authority = activeAuthority() ?: return
        mutex.withLock {
            val current = currentLayout(authority)
            if (current.heroContent == content) return
            val updated = current.copy(heroContent = content)
            persist(authority, updated)
            publish(authority.identity, updated)
        }
    }

    private suspend fun activeAuthority(): Authority? {
        val scope = tokenManager.snapshotCurrentScope()
        val profileId = (if (scope != null) scope.profileId else tokenManager.getProfileId())
            ?.takeIf(String::isNotBlank)
            ?: return null
        val serverId = (if (scope != null) scope.serverId else tokenManager.getCurrentServerId())
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_SERVER_ID
        return Authority(profileId = profileId, serverId = serverId)
    }

    private suspend fun currentLayout(authority: Authority): TvHomeSectionLayout =
        if (_state.value.authority == authority.identity) {
            _state.value.layout
        } else {
            readLayout(authority)
        }

    private suspend fun readLayout(authority: Authority): TvHomeSectionLayout {
        val preferences = storeFor(authority.profileId).data.first()
        return TvHomeSectionLayout(
            orderedSectionIds = unique(decodeIds(preferences[orderKey(authority.serverId)])),
            hiddenSectionIds = preferences[hiddenKey(authority.serverId)].orEmpty(),
            heroSectionId = preferences[heroKey(authority.serverId)]?.takeIf(String::isNotBlank),
            heroDisabled = preferences[heroDisabledKey(authority.serverId)] ?: false,
            heroContent = preferences[heroContentKey(authority.serverId)]
                ?.let { saved -> TvHeroContent.entries.firstOrNull { it.name == saved } }
                ?: TvHeroContent.All,
        )
    }

    private suspend fun persist(authority: Authority, layout: TvHomeSectionLayout) {
        storeFor(authority.profileId).edit { preferences ->
            preferences[orderKey(authority.serverId)] = encodeIds(layout.orderedSectionIds)
            preferences[hiddenKey(authority.serverId)] = layout.hiddenSectionIds
            val heroSectionId = layout.heroSectionId
            if (heroSectionId == null) preferences.remove(heroKey(authority.serverId)) else preferences[heroKey(authority.serverId)] = heroSectionId
            preferences[heroDisabledKey(authority.serverId)] = layout.heroDisabled
            preferences[heroContentKey(authority.serverId)] = layout.heroContent.name
        }
    }

    private fun publishIfChanged(authority: String?, layout: TvHomeSectionLayout) {
        val current = _state.value
        if (current.authority == authority && current.layout == layout) return
        publish(authority, layout)
    }

    private fun publish(authority: String?, layout: TvHomeSectionLayout) {
        _state.value = TvHomeSectionPreferenceState(
            authority = authority,
            layout = layout,
            layoutRevision = _state.value.layoutRevision + 1,
        )
    }

    companion object {
        private const val DEFAULT_SERVER_ID = "default"

        /** Apply saved order, then visibility, without introducing row gaps. */
        fun arrange(
            sections: List<ResolvedSection>,
            layout: TvHomeSectionLayout,
            includingHidden: Boolean = false,
        ): List<ResolvedSection> {
            val rank = layout.orderedSectionIds.withIndex().associate { it.value to it.index }
            val arranged = sections
                .filter { it.items.isNotEmpty() }
                .withIndex()
                .sortedWith { left, right ->
                    val leftRank = rank[left.value.id]
                    val rightRank = rank[right.value.id]
                    when {
                        leftRank != null && rightRank != null -> leftRank.compareTo(rightRank)
                        leftRank != null -> -1
                        rightRank != null -> 1
                        else -> left.index.compareTo(right.index)
                    }
                }
                .map { it.value }

            return if (includingHidden) {
                arranged
            } else {
                arranged.filterNot { it.id in layout.hiddenSectionIds }
            }
        }

        /**
         * The row Home shows as its hero: the chosen row while it has items,
         * else Continue Watching. A row can be the hero while hidden as a row.
         */
        fun hero(sections: List<ResolvedSection>, layout: TvHomeSectionLayout): ResolvedSection? {
            if (layout.heroDisabled) return null
            val populated = arrange(sections, layout, includingHidden = true)
            val continueWatching = populated.firstOrNull { it.isTvContinueWatchingRow() }
            val chosen = layout.heroSectionId
            val row = chosen?.let { id -> populated.firstOrNull { it.id == id } } ?: continueWatching
            return row?.limitedTo(layout.heroContent)
        }

        /** The row with only the titles [content] allows; null when none are left. */
        private fun ResolvedSection.limitedTo(content: TvHeroContent): ResolvedSection? {
            if (content == TvHeroContent.All) return this
            val kept = items.filter { content.accepts(it.type) }
            return if (kept.isEmpty()) null else copy(items = kept)
        }

        internal fun retainedOrder(
            sectionIds: List<String>,
            rememberedSectionIds: List<String>,
        ): List<String> {
            val knownOrder = unique(sectionIds)
            val knownIds = knownOrder.toSet()
            return knownOrder + unique(rememberedSectionIds).filterNot(knownIds::contains)
        }

        private fun orderKey(serverId: String) =
            stringPreferencesKey("home_section_order_${serverHash(serverId)}")

        private fun hiddenKey(serverId: String) =
            stringSetPreferencesKey("home_section_hidden_${serverHash(serverId)}")

        private fun heroKey(serverId: String) =
            stringPreferencesKey("home_hero_section_${serverHash(serverId)}")

        private fun heroContentKey(serverId: String) =
            stringPreferencesKey("home_hero_content_${serverHash(serverId)}")

        private fun heroDisabledKey(serverId: String) =
            booleanPreferencesKey("home_hero_disabled_${serverHash(serverId)}")

        /** Length-prefixing keeps arbitrary server section ids reversible. */
        private fun encodeIds(ids: List<String>): String =
            unique(ids).joinToString(separator = "") { "${it.length}:$it" }

        private fun decodeIds(encoded: String?): List<String> {
            if (encoded.isNullOrEmpty()) return emptyList()
            val result = mutableListOf<String>()
            var cursor = 0
            while (cursor < encoded.length) {
                val separator = encoded.indexOf(':', startIndex = cursor)
                if (separator < 0) return emptyList()
                val length = encoded.substring(cursor, separator).toIntOrNull() ?: return emptyList()
                val start = separator + 1
                val end = start + length
                if (length < 0 || end > encoded.length) return emptyList()
                result += encoded.substring(start, end)
                cursor = end
            }
            return result
        }

        private fun unique(ids: List<String>): List<String> {
            val seen = mutableSetOf<String>()
            return ids.filter { it.isNotBlank() && seen.add(it) }
        }

        private fun fileNameFor(profileId: String): String =
            "tv_home_sections_${profileHash(profileId)}"

        private fun profileHash(profileId: String): String = hash(profileId).take(16)

        private fun serverHash(serverId: String): String = hash(serverId).take(16)

        private fun hash(value: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }
    }
}

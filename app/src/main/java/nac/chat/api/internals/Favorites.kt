package nac.chat.api.internals

import androidx.compose.runtime.mutableStateListOf
import nac.chat.NACApplication
import nac.chat.api.StoatJson
import nac.chat.persistence.KVStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Personal, local-only "favorites" — a quick-access list of user IDs for jumping straight
 * into a DM. This is NOT a friend/server relationship and is never synced to the server
 * (mirrors web's local Favorites concept). Persisted in KVStorage as a JSON array of IDs.
 */
object Favorites {
    private const val KEY = "favoriteUserIds"
    private val scope = CoroutineScope(Dispatchers.IO)

    // Backed by Compose snapshot state so the rail/modal recompose on changes.
    private val _ids = mutableStateListOf<String>()
    val ids: List<String> get() = _ids

    /** Load persisted favorites once at startup. */
    suspend fun hydrate() {
        val raw = KVStorage(NACApplication.instance).get(KEY) ?: return
        val parsed = try {
            StoatJson.decodeFromString(ListSerializer(String.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
        _ids.clear()
        _ids.addAll(parsed)
    }

    fun isFavorite(userId: String): Boolean = _ids.contains(userId)

    fun toggle(userId: String) {
        if (!_ids.remove(userId)) {
            _ids.add(userId)
        }
        persist()
    }

    fun add(userId: String) {
        if (!_ids.contains(userId)) {
            _ids.add(userId)
            persist()
        }
    }

    fun remove(userId: String) {
        if (_ids.remove(userId)) {
            persist()
        }
    }

    private fun persist() {
        val snapshot = _ids.toList()
        scope.launch {
            KVStorage(NACApplication.instance).set(
                KEY,
                StoatJson.encodeToString(ListSerializer(String.serializer()), snapshot)
            )
        }
    }
}

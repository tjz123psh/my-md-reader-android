package com.pang.mdreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pang.mdreader.model.RecentFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore(name = "md_reader_recent")

class RecentFilesRepo(private val context: Context) {

    companion object {
        private val KEY_RECENT = stringPreferencesKey("recent_files")
        private const val MAX_RECENT = 10
    }

    val recentFilesFlow: Flow<List<RecentFile>> = context.recentDataStore.data.map { prefs ->
        val json = prefs[KEY_RECENT] ?: return@map emptyList()
        deserialize(json)
    }

    suspend fun addRecent(file: RecentFile) {
        context.recentDataStore.edit { prefs ->
            val existing = prefs[KEY_RECENT]?.let { deserialize(it) } ?: emptyList()
            val updated = existing
                .filter { it.uri != file.uri }
                .toMutableList()
            updated.add(0, file)
            val trimmed = updated.take(MAX_RECENT)
            prefs[KEY_RECENT] = serialize(trimmed)
        }
    }

    suspend fun removeRecent(uri: String) {
        context.recentDataStore.edit { prefs ->
            val existing = prefs[KEY_RECENT]?.let { deserialize(it) } ?: emptyList()
            val updated = existing.filter { it.uri != uri }
            prefs[KEY_RECENT] = serialize(updated)
        }
    }

    suspend fun clearAll() {
        context.recentDataStore.edit { prefs ->
            prefs.remove(KEY_RECENT)
        }
    }

    private fun serialize(list: List<RecentFile>): String {
        val arr = JSONArray()
        for (f in list) {
            arr.put(JSONObject().apply {
                put("uri", f.uri)
                put("name", f.name)
                put("workspace", f.workspaceUri)
                put("time", f.lastOpened)
            })
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<RecentFile> {
        try {
            val arr = JSONArray(json)
            val result = mutableListOf<RecentFile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    RecentFile(
                        uri = obj.optString("uri", ""),
                        name = obj.optString("name", ""),
                        workspaceUri = obj.optString("workspace", ""),
                        lastOpened = obj.optLong("time", System.currentTimeMillis()),
                    )
                )
            }
            return result
        } catch (_: Exception) {
            return emptyList()
        }
    }
}

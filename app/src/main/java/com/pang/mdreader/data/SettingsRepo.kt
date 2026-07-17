package com.pang.mdreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pang.mdreader.model.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "md_reader_settings")

class SettingsRepo(private val context: Context) {

    companion object {
        private val KEY_ZOOM = intPreferencesKey("document_zoom")
        private val KEY_THEME = stringPreferencesKey("color_scheme")
        private val KEY_LAST_WORKSPACE = stringPreferencesKey("last_workspace_uri")
        private val KEY_LAST_DOCUMENT = stringPreferencesKey("last_document_uri")
        private val KEY_WINDOW_WIDTH = intPreferencesKey("window_width")
        private val KEY_WINDOW_HEIGHT = intPreferencesKey("window_height")
        private val KEY_TOOLBAR_BEHAVIOR = stringPreferencesKey("toolbar_behavior")

        const val DEFAULT_ZOOM = 100
        const val MIN_ZOOM = 75
        const val MAX_ZOOM = 200

        const val TOOLBAR_AUTO_HIDE = "auto_hide"
        const val TOOLBAR_TAP_TOGGLE = "tap_toggle"
    }

    val zoomFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ZOOM] ?: DEFAULT_ZOOM
    }

    val themeFlow: Flow<ReaderTheme> = context.dataStore.data.map { prefs ->
        val id = prefs[KEY_THEME] ?: ReaderTheme.WARM_LIGHT.id
        ReaderTheme.entries.firstOrNull { it.id == id } ?: ReaderTheme.WARM_LIGHT
    }

    val toolbarBehaviorFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TOOLBAR_BEHAVIOR] ?: TOOLBAR_AUTO_HIDE
    }

    val lastWorkspaceFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_WORKSPACE]
    }

    val lastDocumentFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_DOCUMENT]
    }

    suspend fun setZoom(zoom: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ZOOM] = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        }
    }

    suspend fun setTheme(theme: ReaderTheme) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.id
        }
    }

    suspend fun setToolbarBehavior(behavior: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOOLBAR_BEHAVIOR] = behavior
        }
    }

    suspend fun setLastWorkspace(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_LAST_WORKSPACE] = uri else prefs.remove(KEY_LAST_WORKSPACE)
        }
    }

    suspend fun setLastDocument(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_LAST_DOCUMENT] = uri else prefs.remove(KEY_LAST_DOCUMENT)
        }
    }

    suspend fun getZoom(): Int = context.dataStore.data.first()[KEY_ZOOM] ?: DEFAULT_ZOOM
    suspend fun getTheme(): ReaderTheme {
        val id = context.dataStore.data.first()[KEY_THEME] ?: ReaderTheme.WARM_LIGHT.id
        return ReaderTheme.entries.firstOrNull { it.id == id } ?: ReaderTheme.WARM_LIGHT
    }
    suspend fun getToolbarBehavior(): String =
        context.dataStore.data.first()[KEY_TOOLBAR_BEHAVIOR] ?: TOOLBAR_AUTO_HIDE
}

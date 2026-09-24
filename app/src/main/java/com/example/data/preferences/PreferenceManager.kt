package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("atp_builder_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _editorFontSize = MutableStateFlow(getEditorFontSize())
    val editorFontSize: StateFlow<Float> = _editorFontSize.asStateFlow()

    private val _activeProjectId = MutableStateFlow(getActiveProjectId())
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun getEditorFontSize(): Float {
        return prefs.getFloat(KEY_EDITOR_FONT_SIZE, 14f)
    }

    fun setEditorFontSize(size: Float) {
        val clamped = size.coerceIn(10f, 26f)
        prefs.edit().putFloat(KEY_EDITOR_FONT_SIZE, clamped).apply()
        _editorFontSize.value = clamped
    }

    fun getActiveProjectId(): String? {
        return prefs.getString(KEY_ACTIVE_PROJECT_ID, null)
    }

    fun setActiveProjectId(projectId: String?) {
        prefs.edit().putString(KEY_ACTIVE_PROJECT_ID, projectId).apply()
        _activeProjectId.value = projectId
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_EDITOR_FONT_SIZE = "editor_font_size"
        private const val KEY_ACTIVE_PROJECT_ID = "active_project_id"
    }
}

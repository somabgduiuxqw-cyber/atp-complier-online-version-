package com.example.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class EditorTab(
    val filePath: String,
    val fileName: String,
    val content: String,
    val isDirty: Boolean = false,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
)

data class FindReplaceState(
    val isOpen: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val matchCount: Int = 0,
    val currentMatchIndex: Int = 0,
    val matchIndices: List<Int> = emptyList()
)

data class FileNode(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val isExpanded: Boolean = false,
    val children: List<FileNode> = emptyList(),
    val depth: Int = 0
)

class CodeEditorState {
    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _fontSize = MutableStateFlow(14f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _findReplaceState = MutableStateFlow(FindReplaceState())
    val findReplaceState: StateFlow<FindReplaceState> = _findReplaceState.asStateFlow()

    val currentTab: EditorTab?
        get() = _tabs.value.getOrNull(_activeTabIndex.value)

    fun openFile(file: File) {
        val existingIndex = _tabs.value.indexOfFirst { it.filePath == file.absolutePath }
        if (existingIndex >= 0) {
            _activeTabIndex.value = existingIndex
            return
        }

        val text = try {
            if (file.exists() && file.isFile) file.readText() else ""
        } catch (e: Exception) {
            "Error loading file: ${e.localizedMessage}"
        }

        val newTab = EditorTab(
            filePath = file.absolutePath,
            fileName = file.name,
            content = text,
            isDirty = false
        )

        val updated = _tabs.value.toMutableList()
        updated.add(newTab)
        _tabs.value = updated
        _activeTabIndex.value = updated.size - 1
    }

    fun closeTab(index: Int) {
        val current = _tabs.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _tabs.value = current
            if (_activeTabIndex.value >= current.size) {
                _activeTabIndex.value = (current.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
        }
    }

    fun updateContent(newContent: String) {
        val idx = _activeTabIndex.value
        val tab = currentTab ?: return
        if (tab.content == newContent) return

        val newUndo = tab.undoStack.toMutableList()
        newUndo.add(tab.content)
        if (newUndo.size > 50) newUndo.removeAt(0)

        val updatedTab = tab.copy(
            content = newContent,
            isDirty = true,
            undoStack = newUndo,
            redoStack = emptyList()
        )

        val updatedList = _tabs.value.toMutableList()
        updatedList[idx] = updatedTab
        _tabs.value = updatedList
    }

    fun undo() {
        val idx = _activeTabIndex.value
        val tab = currentTab ?: return
        if (tab.undoStack.isEmpty()) return

        val previousContent = tab.undoStack.last()
        val remainingUndo = tab.undoStack.dropLast(1)
        val newRedo = tab.redoStack + tab.content

        val updatedTab = tab.copy(
            content = previousContent,
            isDirty = true,
            undoStack = remainingUndo,
            redoStack = newRedo
        )

        val updatedList = _tabs.value.toMutableList()
        updatedList[idx] = updatedTab
        _tabs.value = updatedList
    }

    fun redo() {
        val idx = _activeTabIndex.value
        val tab = currentTab ?: return
        if (tab.redoStack.isEmpty()) return

        val nextContent = tab.redoStack.last()
        val remainingRedo = tab.redoStack.dropLast(1)
        val newUndo = tab.undoStack + tab.content

        val updatedTab = tab.copy(
            content = nextContent,
            isDirty = true,
            undoStack = newUndo,
            redoStack = remainingRedo
        )

        val updatedList = _tabs.value.toMutableList()
        updatedList[idx] = updatedTab
        _tabs.value = updatedList
    }

    fun saveActiveFile(): Boolean {
        val tab = currentTab ?: return false
        return try {
            val file = File(tab.filePath)
            file.writeText(tab.content)
            val updatedTab = tab.copy(isDirty = false)
            val updatedList = _tabs.value.toMutableList()
            updatedList[_activeTabIndex.value] = updatedTab
            _tabs.value = updatedList
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size.coerceIn(10f, 26f)
    }

    fun toggleFindReplace(show: Boolean) {
        _findReplaceState.value = _findReplaceState.value.copy(isOpen = show)
        if (!show) {
            _findReplaceState.value = FindReplaceState(isOpen = false)
        }
    }

    fun updateSearchQuery(query: String) {
        val tab = currentTab
        if (tab == null || query.isEmpty()) {
            _findReplaceState.value = _findReplaceState.value.copy(
                searchQuery = query,
                matchCount = 0,
                currentMatchIndex = 0,
                matchIndices = emptyList()
            )
            return
        }

        val text = tab.content
        val matches = mutableListOf<Int>()
        var index = text.indexOf(query, 0, ignoreCase = true)
        while (index >= 0) {
            matches.add(index)
            index = text.indexOf(query, index + query.length, ignoreCase = true)
        }

        _findReplaceState.value = _findReplaceState.value.copy(
            searchQuery = query,
            matchCount = matches.size,
            currentMatchIndex = if (matches.isNotEmpty()) 1 else 0,
            matchIndices = matches
        )
    }

    fun updateReplaceQuery(replace: String) {
        _findReplaceState.value = _findReplaceState.value.copy(replaceQuery = replace)
    }

    fun replaceCurrentMatch() {
        val tab = currentTab ?: return
        val state = _findReplaceState.value
        if (state.searchQuery.isEmpty() || state.matchIndices.isEmpty()) return

        val matchIndex = state.matchIndices.getOrNull((state.currentMatchIndex - 1).coerceAtLeast(0)) ?: return
        val before = tab.content.substring(0, matchIndex)
        val after = tab.content.substring(matchIndex + state.searchQuery.length)
        val newContent = before + state.replaceQuery + after

        updateContent(newContent)
        updateSearchQuery(state.searchQuery)
    }

    fun replaceAllMatches() {
        val tab = currentTab ?: return
        val state = _findReplaceState.value
        if (state.searchQuery.isEmpty()) return

        val newContent = tab.content.replace(state.searchQuery, state.replaceQuery, ignoreCase = true)
        updateContent(newContent)
        updateSearchQuery(state.searchQuery)
    }
}

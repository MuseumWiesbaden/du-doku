package de.muwi.scan

import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File


data class AppUiState(
    val code: String = "MUXX-XX-000000", val artist: String = "Museum Wiesbaden"
)

class AppViewModel() : ViewModel() {

    private val dir =
        File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "MuWiCamera")

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun updateLabel(code: String) {
        Log.d("muwi", "Update label: $code")

        _uiState.update { currentState ->
            currentState.copy(code = code)
        }
    }

    fun createSubdir() {
        if (!dir.exists()) {
            val rv = dir.mkdir()
            Log.d("muwi", "Creating directory ${dir.absolutePath} returned $rv.")
        }
    }

    fun updateAuthor(artist: String) {
        Log.d("muwi", "Update author: $artist")

        _uiState.update { currentState ->
            currentState.copy(artist = artist)
        }
    }

    fun getNextFile(): File {
        var count = 0
        var file: File

        do {
            file = getFile(uiState.value.code, ++count)
        } while (file.exists())

        Log.d("muwi", "Next available file : ${file.absolutePath}")

        return file
    }

    private fun getFile(code: String, count: Int): File {
        val filename = "%s_%02d.jpg".format(code, count)
        val file = File(dir, filename)
        return file
    }
}
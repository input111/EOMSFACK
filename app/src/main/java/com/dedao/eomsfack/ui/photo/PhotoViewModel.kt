package com.dedao.eomsfack.ui.photo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PhotoItem(
    val id: String,
    val uri: Uri
)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val _photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val photos: StateFlow<List<PhotoItem>> = _photos.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<String>> = _selectedPhotoIds.asStateFlow()

    private val _showDeleteConfirmationDialog = MutableStateFlow(false)
    val showDeleteConfirmationDialog: StateFlow<Boolean> = _showDeleteConfirmationDialog.asStateFlow()

    private val eomsDir = File(application.getExternalFilesDir(null), "EOMS")

    init {
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!eomsDir.exists()) {
                eomsDir.mkdirs()
            }
            val photoFiles: List<File> = eomsDir.listFiles { file ->
                file.isFile && (file.extension.equals("jpg", ignoreCase = true) || file.extension.equals("png", ignoreCase = true) || file.extension.equals("jpeg", ignoreCase = true))
            }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()

            val photoItems = photoFiles.map {
                val uri = Uri.fromFile(it)
                PhotoItem(id = uri.toString(), uri = uri)
            }
            withContext(Dispatchers.Main) {
                _photos.value = photoItems
            }
        }
    }

    fun saveImageFromUri(sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    val file = File(eomsDir, "IMG_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                loadPhotos()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun enterSelectionMode() {
        _selectionMode.value = true
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedPhotoIds.value = emptySet()
        dismissDeleteConfirmation()
    }

    fun toggleSelection(photoId: String) {
        _selectedPhotoIds.update { current ->
            if (current.contains(photoId)) current - photoId else current + photoId
        }
    }

    fun selectAll() {
        _selectedPhotoIds.update { _photos.value.map { it.id }.toSet() }
    }

    fun showDeleteConfirmation() {
        if (_selectedPhotoIds.value.isNotEmpty()) {
            _showDeleteConfirmationDialog.value = true
        }
    }

    fun dismissDeleteConfirmation() {
        _showDeleteConfirmationDialog.value = false
    }

    fun confirmDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedIds = _selectedPhotoIds.value
            selectedIds.forEach { id ->
                try {
                    val file = File(Uri.parse(id).path!!)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                exitSelectionMode()
            }
            loadPhotos()
        }
    }
}
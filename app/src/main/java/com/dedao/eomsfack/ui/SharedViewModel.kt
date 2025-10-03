package com.dedao.eomsfack.ui

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dedao.eomsfack.data.WorkOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    // --- Work Order State ---
    private val _workOrders = MutableStateFlow<List<WorkOrder>>(emptyList())
    val workOrders: StateFlow<List<WorkOrder>> = _workOrders.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDate = MutableStateFlow<Long?>(null)
    val selectedDate: StateFlow<Long?> = _selectedDate.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedWorkOrderIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedWorkOrderIds: StateFlow<Set<String>> = _selectedWorkOrderIds.asStateFlow()

    private val _showDeleteConfirmationDialog = MutableStateFlow(false)
    val showDeleteConfirmationDialog: StateFlow<Boolean> = _showDeleteConfirmationDialog.asStateFlow()

    val filteredWorkOrders: StateFlow<List<WorkOrder>> =
        combine(_workOrders, _searchQuery, _selectedDate) { orders, query, date ->
            orders.filter { workOrder ->
                val matchesSearch = query.isBlank() ||
                        workOrder.workOrderId.contains(query, ignoreCase = true) ||
                        workOrder.oldPonPort.contains(query, ignoreCase = true) ||
                        workOrder.newPonPort.contains(query, ignoreCase = true)

                val matchesDate = date == null || isSameDay(workOrder.timestamp, date)
                matchesSearch && matchesDate
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return sdf.format(Date(timestamp1)) == sdf.format(Date(timestamp2))
    }

    fun addWorkOrder(workOrder: WorkOrder) {
        _workOrders.update { listOf(workOrder) + it }
    }

    fun markEmailSent(workOrderId: String) {
        _workOrders.update { currentList ->
            currentList.map {
                if (it.workOrderId == workOrderId) it.copy(isEmailSent = true) else it
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedDate(date: Long?) {
        _selectedDate.value = date
    }

    fun enterSelectionMode() {
        _selectionMode.value = true
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedWorkOrderIds.value = emptySet()
    }

    fun toggleSelection(workOrderId: String) {
        _selectedWorkOrderIds.update { if (it.contains(workOrderId)) it - workOrderId else it + workOrderId }
    }

    fun selectAll() {
        _selectedWorkOrderIds.update { filteredWorkOrders.value.map { it.id }.toSet() }
    }

    fun deleteSelectedWorkOrders() {
        if (_selectedWorkOrderIds.value.isNotEmpty()) {
            _showDeleteConfirmationDialog.value = true
        }
    }

    fun confirmDeleteSelectedWorkOrders() {
        _workOrders.update { currentList ->
            currentList.filterNot { selectedWorkOrderIds.value.contains(it.id) }
        }
        _selectedWorkOrderIds.value = emptySet()
        _selectionMode.value = false
        _showDeleteConfirmationDialog.value = false
    }

    fun dismissDeleteConfirmation() {
        _showDeleteConfirmationDialog.value = false
    }

    // --- Photo Preview State ---
    private val _previewImageUri = MutableStateFlow<Uri?>(null)
    val previewImageUri: StateFlow<Uri?> = _previewImageUri

    fun getRandomPhotoAndCopyToDcim() {
        viewModelScope.launch(Dispatchers.IO) {
            val eomsDir = File(getApplication<Application>().getExternalFilesDir(null), "EOMS")
            if (!eomsDir.exists() || !eomsDir.isDirectory) {
                _previewImageUri.value = null
                return@launch
            }

            val photoFiles = eomsDir.listFiles { file ->
                file.isFile && (file.extension.equals("jpg", ignoreCase = true) ||
                        file.extension.equals("png", ignoreCase = true) ||
                        file.extension.equals("jpeg", ignoreCase = true))
            }

            if (photoFiles.isNullOrEmpty()) {
                _previewImageUri.value = null
                return@launch
            }

            val randomFile = photoFiles.random()
            val sourceUri = Uri.fromFile(randomFile)
            _previewImageUri.value = sourceUri

            copyUriToDcim(sourceUri, randomFile.name)
        }
    }

    private fun copyUriToDcim(sourceUri: Uri, fileName: String) {
        val context = getApplication<Application>()
        val contentResolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "EOMS_${System.currentTimeMillis()}_${fileName}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            }
        }

        val destinationUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        destinationUri?.let { destUri ->
            try {
                contentResolver.openOutputStream(destUri)?.use { outputStream ->
                    contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearPreviewImage() {
        _previewImageUri.value = null
    }
}
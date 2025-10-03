package com.dedao.eomsfack.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dedao.eomsfack.data.WorkOrder
import com.dedao.eomsfack.ui.SharedViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HistoryScreen(sharedViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: SharedViewModel) {
    val workOrders by viewModel.filteredWorkOrders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedWorkOrderIds by viewModel.selectedWorkOrderIds.collectAsStateWithLifecycle()
    val showDeleteConfirmationDialog by viewModel.showDeleteConfirmationDialog.collectAsStateWithLifecycle()

        val context = LocalContext.current

        Scaffold(
            topBar = {
                if (selectionMode) {
                    SelectionTopAppBar(
                        onCloseClick = { viewModel.exitSelectionMode() },
                        onSelectAllClick = { viewModel.selectAll() },
                        onDeleteClick = { viewModel.deleteSelectedWorkOrders() },
                        selectedCount = selectedWorkOrderIds.size
                    )
                } else {
                    NormalTopAppBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onCalendarClick = {
                            val datePicker = MaterialDatePicker.Builder.datePicker()
                                .setTitleText("选择日期")
                                .setSelection(selectedDate ?: MaterialDatePicker.todayInUtcMilliseconds())
                                .build()
                            datePicker.addOnPositiveButtonClickListener { selection ->
                                viewModel.setSelectedDate(selection)
                            }
                            datePicker.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return@NormalTopAppBar, datePicker.toString())
                        },
                        onClearDateClick = { viewModel.setSelectedDate(null) },
                        isDateSelected = selectedDate != null
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (workOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "暂无工单记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(workOrders, key = { it.id }) { workOrder ->
                            WorkOrderCard(
                                workOrder = workOrder,
                                selectionMode = selectionMode,
                                isSelected = selectedWorkOrderIds.contains(workOrder.id),
                                onToggleSelection = { viewModel.toggleSelection(workOrder.id) },
                                onLongPress = {
                                    if (!selectionMode) {
                                        viewModel.enterSelectionMode()
                                        viewModel.toggleSelection(workOrder.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteConfirmation() },
                title = { Text("确认删除") },
                text = { Text("您确定要删除选中的 ${selectedWorkOrderIds.size} 条工单记录吗？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDeleteSelectedWorkOrders() }) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                        Text("取消")
                    }
                }
            )
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormalTopAppBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCalendarClick: () -> Unit,
    onClearDateClick: () -> Unit,
    isDateSelected: Boolean
) {
    TopAppBar(
        title = { Text("工单记录") },
        actions = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("搜索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            IconButton(onClick = onCalendarClick) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "选择日期")
            }
            if (isDateSelected) {
                IconButton(onClick = onClearDateClick) {
                    Icon(Icons.Default.Close, contentDescription = "清除日期")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    onCloseClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onDeleteClick: () -> Unit,
    selectedCount: Int
) {
    TopAppBar(
        title = { Text("已选择 $selectedCount 项") },
        navigationIcon = {
            IconButton(onClick = onCloseClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "关闭选择模式")
            }
        },
        actions = {
            TextButton(onClick = onSelectAllClick) {
                Text("全选")
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "删除选中项")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkOrderCard(
    workOrder: WorkOrder,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelection() },
                onLongClick = onLongPress
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "工单编号: ${workOrder.workOrderId}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "老PON口: ${workOrder.oldPonPort}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "新PON口: ${workOrder.newPonPort}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "邮件状态: ${if (workOrder.isEmailSent) "已发送" else "未发送"}", style = MaterialTheme.typography.bodySmall)
                Text(text = "时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(workOrder.timestamp))}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
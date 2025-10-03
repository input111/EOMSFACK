package com.dedao.eomsfack.ui.home

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dedao.eomsfack.data.WorkOrder
import com.dedao.eomsfack.ui.SharedViewModel
import kotlinx.coroutines.launch
import java.util.UUID

class HomeFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                EOMSfackScreen(sharedViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EOMSfackScreen(viewModel: SharedViewModel) {
    var workOrderId by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var oldPonPort by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var newPonPort by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var generatedText by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val previewImageUri by viewModel.previewImageUri.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.getRandomPhotoAndCopyToDcim()
            scope.launch {
                snackbarHostState.showSnackbar("权限已获取，请再次点击生成")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("无法获取存储权限，无法复制图片")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("主页") },
                actions = {
                    IconButton(onClick = {
                        workOrderId = TextFieldValue("")
                        oldPonPort = TextFieldValue("")
                        newPonPort = TextFieldValue("")
                        generatedText = ""
                        viewModel.clearPreviewImage()
                        scope.launch {
                            snackbarHostState.showSnackbar("所有内容已清空")
                        }
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空所有内容")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = workOrderId,
                onValueChange = { workOrderId = it },
                label = { Text("工单编号") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = oldPonPort,
                onValueChange = { oldPonPort = it },
                label = { Text("老PON口") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newPonPort,
                onValueChange = { newPonPort = it },
                label = { Text("新PON口") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    val generatedString = """
                        工单编号：${workOrderId.text}

                        消除原因：该PON口已割接至新PON口，用户侧已正常，麻烦老师网管侧消除告警

                        老PON口名称：${oldPonPort.text}
                        新PON口名称：${newPonPort.text}
                    """.trimIndent()
                    generatedText = generatedString

                    val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        viewModel.getRandomPhotoAndCopyToDcim()
                    } else {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, permission) -> {
                                viewModel.getRandomPhotoAndCopyToDcim()
                            }
                            else -> {
                                permissionLauncher.launch(permission)
                            }
                        }
                    }

                    if (workOrderId.text.isNotBlank() && oldPonPort.text.isNotBlank()) {
                        val newWorkOrder = WorkOrder(
                            id = UUID.randomUUID().toString(),
                            workOrderId = workOrderId.text,
                            oldPonPort = oldPonPort.text,
                            newPonPort = newPonPort.text,
                            generatedText = generatedString
                        )
                        viewModel.addWorkOrder(newWorkOrder)
                        scope.launch {
                            snackbarHostState.showSnackbar("生成成功")
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("工单号和老PON口不能为空")
                        }
                    }
                }) {
                    Text("生成")
                }
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("EOMSfack Text", generatedText)
                    clipboard.setPrimaryClip(clip)
                    scope.launch {
                        snackbarHostState.showSnackbar("文本已复制")
                    }
                }) {
                    Text("复制")
                }
                Button(onClick = {
                    if (workOrderId.text.isNotBlank() && generatedText.isNotBlank()) {
                        viewModel.markEmailSent(workOrderId.text)

                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_SUBJECT, "工单处理: ${workOrderId.text}")
                            putExtra(Intent.EXTRA_TEXT, generatedText)
                        }
                        try {
                            context.startActivity(emailIntent)
                            scope.launch {
                                snackbarHostState.showSnackbar("正在打开邮件应用...")
                            }
                        } catch (e: Exception) {
                            scope.launch {
                                snackbarHostState.showSnackbar("未找到邮件应用")
                            }
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("请先生成工单内容")
                        }
                    }
                }) {
                    Text("发送邮件")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "文本预览框:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        border = BorderStroke(1.dp, Color.Gray),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(text = generatedText, modifier = Modifier.padding(16.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "图片预览框:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        border = BorderStroke(1.dp, Color.Gray),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (previewImageUri != null) {
                            AsyncImage(
                                model = previewImageUri,
                                contentDescription = "预览照片",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "图片预览")
                            }
                        }
                    }
                }
            }
        }
    }
}
package com.coolfxin.simplecrypto

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.provider.MediaStore
import android.os.Build
import com.coolfxin.simplecrypto.auth.AuthViewModel
import com.coolfxin.simplecrypto.auth.NavigationRoute
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileEncryptionScreen(
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val viewModel: FileEncryptionViewModel = viewModel {
        FileEncryptionViewModel(context.applicationContext as Application, authViewModel)
    }
    val scrollState = rememberScrollState()

    // 权限检查
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.loadEncryptedFiles()
        } else {
            Toast.makeText(context, "需要存储权限才能操作文件", Toast.LENGTH_SHORT).show()
        }
    }

    // 多选文件选择器
    val multipleFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.selectFiles(uris)
        }
    }

    // Android 14+ 删除媒体文件的请求 launcher
    val deleteMediaLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                // 用户确认删除
                viewModel.onDeleteCompleted(true)
                viewModel.loadEncryptedFiles()
                viewModel.setOperationResult(FileEncryptionViewModel.OperationResult.Success("加密完成！原始文件已删除"))
            } else {
                // 用户取消删除
                viewModel.onDeleteCompleted(false)
                viewModel.loadEncryptedFiles()
                viewModel.setOperationResult(FileEncryptionViewModel.OperationResult.Success("加密完成！（原始文件未删除）"))
            }
        }
    } else {
        null
    }

    LaunchedEffect(Unit) {
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                multiplePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                )
            } else {
                multiplePermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        } else {
            viewModel.loadEncryptedFiles()
        }
    }

    // 监听待删除的文件，直接启动系统删除对话框
    LaunchedEffect(viewModel.pendingDeleteUris) {
        if (viewModel.pendingDeleteUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && deleteMediaLauncher != null) {
                // Android 14+ 使用系统删除对话框
                try {
                    val deleteRequest = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        viewModel.pendingDeleteUris
                    )

                    deleteMediaLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
                } catch (e: Exception) {
                    viewModel.onDeleteCompleted(false)
                    viewModel.setOperationResult(FileEncryptionViewModel.OperationResult.Error("删除失败：${e.message}"))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "文件加密器",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { authViewModel.navigateTo(NavigationRoute.SETTINGS) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize()
        ) {
            // 文件选择区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择要加密的文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                multipleFilePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("选择图片")
                        }

                        Button(
                            onClick = {
                                multipleFilePickerLauncher.launch("video/*")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("选择视频")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 删除原始文件开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "加密后删除原始文件",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (viewModel.deleteOriginalAfterEncrypt) {
                                    "加密完成后将删除原始文件"
                                } else {
                                    "加密完成后保留原始文件"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = viewModel.deleteOriginalAfterEncrypt,
                            onCheckedChange = { viewModel.toggleDeleteOriginal(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 已选择的文件列表
                    if (viewModel.selectedFiles.isNotEmpty()) {
                        Text(
                            text = "已选择 ${viewModel.selectedFiles.size} 个文件",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(viewModel.selectedFiles) { selectedFile ->
                                SelectedFileItem(
                                    file = selectedFile,
                                    onRemove = { viewModel.removeFile(selectedFile) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                // 登录后加密密码已经缓存，直接加密
                                viewModel.encryptSelectedFiles()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.isProcessing && viewModel.selectedFiles.isNotEmpty()
                        ) {
                            if (viewModel.isProcessing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("正在加密...")
                                    viewModel.processingFileName?.let { name ->
                                        Text(" ($name)", maxLines = 1)
                                    }
                                }
                            } else {
                                Text("加密")
                            }
                        }

                        // 清空按钮
                        TextButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("清空选择")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 操作结果状态显示
            if (viewModel.operationResult != null) {
                val operationResult = viewModel.operationResult
                val (isSuccess, message) = when (operationResult) {
                    is FileEncryptionViewModel.OperationResult.Success -> true to operationResult.message
                    is FileEncryptionViewModel.OperationResult.Error -> false to operationResult.message
                    null -> false to ""
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Error,
                            contentDescription = if (isSuccess) "成功" else "错误",
                            tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 加密文件列表
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已加密文件 (${viewModel.encryptedFiles.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(onClick = {
                            viewModel.clearSelection()
                            viewModel.loadEncryptedFiles()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (viewModel.isProcessing && viewModel.encryptedFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (viewModel.encryptedFiles.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "无加密文件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "没有找到已加密文件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "请选择文件进行加密",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 400.dp)
                        ) {
                            items(viewModel.encryptedFiles) { encryptedFile ->
                                EncryptedFileItem(
                                    file = encryptedFile,
                                    viewModel = viewModel,
                                    authViewModel = authViewModel
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 底部状态栏
            if (viewModel.statusMessage.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = viewModel.statusMessage,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedFileItem(
    file: SelectedFile,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.mimeType.startsWith("image"))
                    Icons.Default.Image else Icons.Default.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = if (file.cacheFile != null) "缓存文件" else "原始文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EncryptedFileItem(
    file: File,
    viewModel: FileEncryptionViewModel,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    var showDecryptDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.decryptFile(file, uri)
            showDecryptDialog = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val fileName = file.name.replace(".enc", "")
                Icon(
                    imageVector = if (fileName.endsWith(".jpg") || fileName.endsWith(".png") ||
                        fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
                        fileName.endsWith(".bmp") || fileName.endsWith(".webp"))
                        Icons.Default.Image
                    else Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = FileUtils.getFileSize(file),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 预览按钮
                IconButton(onClick = { authViewModel.previewEncryptedFile(file) }) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "预览",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { showDecryptDialog = true }) {
                    Icon(imageVector = Icons.Default.Restore, contentDescription = "解密")
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // 解密对话框
    if (showDecryptDialog) {
        AlertDialog(
            onDismissRequest = { showDecryptDialog = false },
            title = { Text("解密文件") },
            text = {
                Column {
                    Text("将使用您设置的加密密码解密文件")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = file.name.replace(".enc", ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击确定后，您将选择解密文件的保存位置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val fileName = file.name.replace(".enc", "")
                    saveFileLauncher.launch(fileName)
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(onClick = { showDecryptDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除确认") },
            text = {
                Column {
                    Text("确定要删除这个加密文件吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = file.name.replace(".enc", ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FileUtils.getFileSize(file),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "警告：删除后无法恢复！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEncryptedFile(file)
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

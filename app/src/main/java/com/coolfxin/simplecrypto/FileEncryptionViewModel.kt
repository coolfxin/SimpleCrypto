package com.coolfxin.simplecrypto

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coolfxin.simplecrypto.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 文件信息数据类
 */
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val path: String?,
    val mimeType: String,
    val cacheFile: File? = null
)

class FileEncryptionViewModel(
    application: Application,
    private val authViewModel: AuthViewModel
) : AndroidViewModel(application) {

    // 多选文件列表
    private val _selectedFiles = mutableStateOf<List<SelectedFile>>(emptyList())
    val selectedFiles get() = _selectedFiles.value

    private val _statusMessage = mutableStateOf("")
    val statusMessage get() = _statusMessage.value

    private val _isProcessing = mutableStateOf(false)
    val isProcessing get() = _isProcessing.value

    private val _encryptedFiles = mutableStateOf<List<File>>(emptyList())
    val encryptedFiles get() = _encryptedFiles.value

    private val _operationResult = mutableStateOf<OperationResult?>(null)
    val operationResult get() = _operationResult.value

    private val _processingFileName = mutableStateOf<String?>(null)
    val processingFileName get() = _processingFileName.value

    // 加密后是否删除原始文件的开关
    private val _deleteOriginalAfterEncrypt = mutableStateOf(true)
    val deleteOriginalAfterEncrypt get() = _deleteOriginalAfterEncrypt.value

    fun toggleDeleteOriginal(value: Boolean) {
        _deleteOriginalAfterEncrypt.value = value
        android.util.Log.d("ViewModel", "Delete original after encrypt: $value")
    }

    // 待删除的文件 URI 列表（用于 Android 14+ 批量删除确认）
    private val _pendingDeleteUris = mutableStateOf<List<Uri>>(emptyList())
    val pendingDeleteUris get() = _pendingDeleteUris.value

    // 待删除的文件信息（用于删除后继续处理）
    private val _pendingDeleteFiles = mutableStateOf<List<PendingDeleteItem>>(emptyList())
    val pendingDeleteFiles get() = _pendingDeleteFiles.value

    data class PendingDeleteItem(
        val uri: Uri,
        val cacheFile: File?,
        val encryptedFile: File
    )

    // 设置待删除的文件
    fun setPendingDeleteFiles(context: Context, items: List<PendingDeleteItem>) {
        _pendingDeleteFiles.value = items

        // 将 URI 转换为 MediaStore URI 格式
        val convertedUris = items.mapNotNull { item ->
            convertPhotoPickerToMediaStoreUri(context, item.uri)
        }

        android.util.Log.d("ViewModel", "Original URIs: ${items.map { it.uri }}")
        android.util.Log.d("ViewModel", "Converted URIs: $convertedUris")

        _pendingDeleteUris.value = convertedUris
    }

    /**
     * 将 Photo Picker URI 转换为标准 MediaStore URI
     * content://media/picker_get_content/.../media/1000001942
     * → content://media/external/images/media/1000001942
     */
    private fun convertPhotoPickerToMediaStoreUri(context: Context, uri: Uri): Uri? {
        if (!uri.toString().contains("picker_get_content")) {
            // 不是 Photo Picker URI，直接返回
            return uri
        }

        val mediaId = uri.lastPathSegment ?: return null
        android.util.Log.d("ViewModel", "Converting Photo Picker URI: $uri, media ID: $mediaId")

        // 尝试从 Images 表查找
        val projection = arrayOf(
            android.provider.MediaStore.Images.Media._ID,
            android.provider.MediaStore.Images.Media.MIME_TYPE
        )

        var result: Uri? = null

        context.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${android.provider.MediaStore.Images.Media._ID} = ?",
            arrayOf(mediaId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                android.util.Log.d("ViewModel", "Found in Images table")
                result = android.net.Uri.withAppendedPath(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                )
            }
        }

        if (result != null) {
            return result
        }

        // 尝试从 Videos 表查找
        context.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                android.provider.MediaStore.Video.Media._ID,
                android.provider.MediaStore.Video.Media.MIME_TYPE
            ),
            "${android.provider.MediaStore.Video.Media._ID} = ?",
            arrayOf(mediaId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                android.util.Log.d("ViewModel", "Found in Videos table")
                result = android.net.Uri.withAppendedPath(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                )
            }
        }

        if (result == null) {
            android.util.Log.w("ViewModel", "Media ID $mediaId not found in MediaStore")
        }

        return result
    }

    // 完成删除后的回调
    fun onDeleteCompleted(success: Boolean) {
        if (success) {
            // 删除成功，清理缓存文件
            _pendingDeleteFiles.value.forEach { item ->
                item.cacheFile?.let {
                    FileUtils.deleteFile(it)
                }
                Log.d("ViewModel", "Deleted cache file: ${item.cacheFile?.absolutePath}")
            }
        } else {
            // 用户取消或失败，也清理缓存文件（原始文件还在）
            _pendingDeleteFiles.value.forEach { item ->
                item.cacheFile?.let {
                    FileUtils.deleteFile(it)
                }
                Log.d("ViewModel", "Cleaned up cache file after cancelled deletion: ${item.cacheFile?.absolutePath}")
            }
        }
        _pendingDeleteFiles.value = emptyList()
        _pendingDeleteUris.value = emptyList()
        Log.d("ViewModel", "Delete operation completed, success: $success")
    }

    // 设置操作结果（供 UI 层调用）
    fun setOperationResult(result: OperationResult) {
        _operationResult.value = result
    }

    // 清除操作结果
    fun clearOperationResult() {
        _operationResult.value = null
    }

    /**
     * 执行删除操作（用于自定义对话框确认后）
     */
    fun performDelete(context: Context, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val uris = _pendingDeleteUris.value
            var successCount = 0
            var failCount = 0

            Log.d("ViewModel", "Performing delete for ${uris.size} URIs")

            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val deleted = FileUtils.deleteMediaFile(context, uri)
                    if (deleted) {
                        successCount++
                        Log.d("ViewModel", "Successfully deleted: $uri")
                    } else {
                        failCount++
                        Log.w("ViewModel", "Failed to delete: $uri")
                    }
                }
            }

            // 返回是否全部成功
            callback(failCount == 0)
        }
    }

    private val tempCacheFiles = mutableListOf<File>()

    sealed class OperationResult {
        data class Success(val message: String) : OperationResult()
        data class Error(val message: String) : OperationResult()
    }

    init {
        loadEncryptedFiles()
    }

    /**
     * 添加多个文件到选择列表
     */
    fun selectFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val newFiles = uris.mapNotNull { uri ->
                try {
                    val fileName = FileUtils.getFileName(getApplication(), uri) ?: return@mapNotNull null
                    val mimeType = getApplication<Application>().contentResolver.getType(uri) ?: "*/*"
                    val filePath = FileUtils.getOriginalFilePath(getApplication(), uri)

                    Log.d("ViewModel", "Selected file - URI: $uri, Name: $fileName, Path: $filePath, MIME: $mimeType")

                    // 检查文件是否可访问
                    val isPathAccessible = FileUtils.isFileAccessible(filePath)

                    if (!isPathAccessible) {
                        Log.w("ViewModel", "Original file path not accessible, creating cache copy")
                        // 复制文件到缓存
                        val cacheFile = FileUtils.copyUriToCache(getApplication(), uri)
                        if (cacheFile != null && cacheFile.exists()) {
                            tempCacheFiles.add(cacheFile)
                            SelectedFile(uri, fileName, cacheFile.absolutePath, mimeType, cacheFile)
                        } else {
                            Log.e("ViewModel", "Failed to create cache copy for $fileName")
                            null
                        }
                    } else {
                        SelectedFile(uri, fileName, filePath, mimeType, null)
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Error processing file $uri", e)
                    null
                }
            }

            _selectedFiles.value = _selectedFiles.value + newFiles
            _statusMessage.value = "已选择 ${_selectedFiles.value.size} 个文件"
        }
    }

    /**
     * 移除指定的文件
     */
    fun removeFile(file: SelectedFile) {
        val updatedList = _selectedFiles.value.toMutableList()
        updatedList.remove(file)
        _selectedFiles.value = updatedList

        // 清理缓存文件
        file.cacheFile?.let {
            FileUtils.deleteFile(it)
            tempCacheFiles.remove(it)
        }

        _statusMessage.value = "已选择 ${_selectedFiles.value.size} 个文件"
    }

    /**
     * 清空选择
     */
    fun clearSelection() {
        _selectedFiles.value.forEach { file ->
            file.cacheFile?.let {
                FileUtils.deleteFile(it)
            }
        }
        tempCacheFiles.clear()
        _selectedFiles.value = emptyList()
        _operationResult.value = null
        _statusMessage.value = ""
    }

    /**
     * 批量加密所有选中的文件
     * 修改：先加密所有文件，然后收集需要删除的 URI 供 UI 层处理
     */
    fun encryptSelectedFiles() {
        val filesToEncrypt = _selectedFiles.value
        if (filesToEncrypt.isEmpty()) {
            _operationResult.value = OperationResult.Error("请先选择文件")
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _operationResult.value = null
            // 清空之前的待删除列表
            _pendingDeleteFiles.value = emptyList()
            _pendingDeleteUris.value = emptyList()

            var successCount = 0
            var failCount = 0
            val results = mutableListOf<String>()
            val pendingDeleteItems = mutableListOf<PendingDeleteItem>()

            filesToEncrypt.forEachIndexed { index, selectedFile ->
                try {
                    _processingFileName.value = selectedFile.name
                    _statusMessage.value = "正在加密 (${index + 1}/${filesToEncrypt.size}): ${selectedFile.name}"

                    val filePath = selectedFile.path
                        ?: throw IllegalArgumentException("文件路径为空")

                    val originalFile = File(filePath)

                    if (!originalFile.exists()) {
                        results.add("❌ ${selectedFile.name}: 文件不存在")
                        failCount++
                        return@forEachIndexed
                    }

                    val context = getApplication<Application>().applicationContext
                    val encryptedDir = FileUtils.getEncryptedFilesDir(context)

                    if (!encryptedDir.exists()) {
                        encryptedDir.mkdirs()
                    }

                    val encryptedFile = File(encryptedDir, "${selectedFile.name}.enc")

                    // 从 AuthViewModel 获取加密密码
                    val password = authViewModel.getEncryptionPassword()
                    if (password == null) {
                        _operationResult.value = OperationResult.Error("NEED_PASSWORD_VERIFICATION")
                        _isProcessing.value = false
                        return@launch
                    }

                    val success = withContext(Dispatchers.IO) {
                        CryptoUtils.encryptFile(password, originalFile, encryptedFile)
                    }

                    if (success) {
                        // 加密成功，根据开关状态决定是否删除原始文件
                        if (_deleteOriginalAfterEncrypt.value) {
                            val isPhotoPickerUri = selectedFile.uri.toString().contains("picker_get_content")

                            if (selectedFile.cacheFile != null && !isPhotoPickerUri) {
                                // 缓存文件且不是 Photo Picker URI，直接删除
                                FileUtils.deleteFile(originalFile)
                                tempCacheFiles.remove(selectedFile.cacheFile)
                                results.add("✅ ${selectedFile.name}: 加密成功，已删除原始文件")
                            } else {
                                // Photo Picker URI 或非缓存文件，添加到待删除列表
                                pendingDeleteItems.add(
                                    PendingDeleteItem(
                                        uri = selectedFile.uri,
                                        cacheFile = selectedFile.cacheFile,
                                        encryptedFile = encryptedFile
                                    )
                                )
                                results.add("✅ ${selectedFile.name}: 加密成功，等待确认删除")
                            }
                        } else {
                            // 开关关闭，不删除原始文件
                            if (selectedFile.cacheFile != null) {
                                // 清理缓存文件
                                FileUtils.deleteFile(selectedFile.cacheFile)
                                tempCacheFiles.remove(selectedFile.cacheFile)
                            }
                            results.add("✅ ${selectedFile.name}: 加密成功（保留原始文件）")
                        }
                        successCount++
                    } else {
                        results.add("❌ ${selectedFile.name}: 加密失败")
                        failCount++
                        if (encryptedFile.exists()) {
                            FileUtils.deleteFile(encryptedFile)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Error encrypting ${selectedFile.name}", e)
                    results.add("❌ ${selectedFile.name}: ${e.message}")
                    failCount++
                }
            }

            // 加载更新后的加密文件列表
            loadEncryptedFiles()

            // 清空已选择的文件
            tempCacheFiles.clear()
            _selectedFiles.value = emptyList()
            _processingFileName.value = null

            // 如果有待删除的文件，设置它们供 UI 层处理
            if (pendingDeleteItems.isNotEmpty()) {
                val context = getApplication<Application>().applicationContext
                setPendingDeleteFiles(context, pendingDeleteItems)
                // 暂时不显示结果，等待用户确认删除后再显示
                _isProcessing.value = false
                // UI 层会检测到 pendingDeleteUris 并弹出删除确认对话框
            } else {
                // 没有需要删除的文件，直接显示结果
                val summary = "加密完成！成功: $successCount, 失败: $failCount"
                _operationResult.value = OperationResult.Success(summary + "\n\n" + results.joinToString("\n"))
                _isProcessing.value = false
                kotlinx.coroutines.delay(3000)
                _operationResult.value = null
            }
        }
    }

    /**
     * 加密单个文件（保留用于兼容）
     */
    fun encryptSelectedFile() {
        encryptSelectedFiles()
    }

    /**
     * 解密单个文件
     */
    fun decryptFile(encryptedFile: File, outputUri: Uri?) {
        // 从 AuthViewModel 获取加密密码
        val password = authViewModel.getEncryptionPassword()
        if (password == null) {
            _operationResult.value = OperationResult.Error("加密密码未配置")
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "开始解密文件..."
            _operationResult.value = null

            try {
                val context = getApplication<Application>().applicationContext

                // 如果用户选择了输出位置
                if (outputUri != null) {
                    _statusMessage.value = "正在解密文件..."

                    // 解密到临时文件
                    val tempDecryptedFile = File(context.cacheDir, "temp_decrypted_${System.currentTimeMillis()}")
                    val success = CryptoUtils.decryptFile(password, encryptedFile, tempDecryptedFile)

                    if (!success) {
                        throw Exception("密码错误或文件已损坏")
                    }

                    // 复制到用户选择的位置
                    val outputStream = context.contentResolver.openOutputStream(outputUri)
                    if (outputStream != null) {
                        try {
                            val inputStream = FileInputStream(tempDecryptedFile)
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            inputStream.close()
                        } finally {
                            outputStream.close()
                        }

                        // 删除临时文件
                        tempDecryptedFile.delete()

                        // 删除加密文件
                        val deleteSuccess = FileUtils.deleteFile(encryptedFile)
                        if (deleteSuccess) {
                            _operationResult.value = OperationResult.Success("✅ 文件解密成功！\n已保存到选择的位置")
                        } else {
                            _operationResult.value = OperationResult.Success("⚠️ 文件解密成功，但删除加密文件失败")
                        }
                    } else {
                        throw Exception("无法打开输出流")
                    }
                } else {
                    throw Exception("请选择保存位置")
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "decryptFile error", e)
                _operationResult.value = OperationResult.Error("❌ 解密失败: ${e.message}")
            } finally {
                _isProcessing.value = false
                kotlinx.coroutines.delay(2000)
                _operationResult.value = null
                loadEncryptedFiles()
            }
        }
    }

    /**
     * 通过 ContentResolver 删除原始文件
     */
    private fun deleteOriginalFile(context: Application, uri: Uri, file: File): Boolean {
        return try {
            // 使用改进的删除方法
            val deleted = FileUtils.deleteMediaFile(context, uri)
            if (deleted) {
                Log.d("ViewModel", "File deleted via deleteMediaFile")
                true
            } else {
                // MediaStore 删除失败，尝试直接删除文件
                Log.d("ViewModel", "MediaStore delete failed, trying direct delete")
                FileUtils.deleteFile(file)
            }
        } catch (e: Exception) {
            Log.e("ViewModel", "Error deleting file", e)
            // 尝试直接删除文件
            FileUtils.deleteFile(file)
        }
    }

    fun loadEncryptedFiles() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val encryptedDir = FileUtils.getEncryptedFilesDir(context)

                Log.d("ViewModel", "Loading encrypted files from: ${encryptedDir.absolutePath}")

                withContext(Dispatchers.IO) {
                    if (!encryptedDir.exists()) {
                        Log.w("ViewModel", "Encrypted directory does not exist")
                        _encryptedFiles.value = emptyList()
                        return@withContext
                    }

                    val files = encryptedDir.listFiles()
                    Log.d("ViewModel", "Found ${files?.size ?: 0} files in directory")

                    val validFiles = files?.filter { it.isFile && it.name.endsWith(".enc") } ?: emptyList()
                    Log.d("ViewModel", "Found ${validFiles.size} valid encrypted files")

                    _encryptedFiles.value = validFiles
                }

                if (_encryptedFiles.value.isEmpty()) {
                    _statusMessage.value = "没有找到已加密文件"
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "loadEncryptedFiles error", e)
                _statusMessage.value = "加载加密文件失败: ${e.message}"
            }
        }
    }

    /**
     * 删除指定的加密文件
     */
    fun deleteEncryptedFile(file: File) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val deleted = FileUtils.deleteFile(file)
                    if (deleted) {
                        Log.d("ViewModel", "Deleted encrypted file: ${file.name}")
                    } else {
                        Log.w("ViewModel", "Failed to delete encrypted file: ${file.name}")
                    }
                }

                // 重新加载文件列表
                loadEncryptedFiles()
                _operationResult.value = OperationResult.Success("已删除加密文件")
            } catch (e: Exception) {
                Log.e("ViewModel", "deleteEncryptedFile error", e)
                _operationResult.value = OperationResult.Error("删除文件失败: ${e.message}")
            }
        }
    }
}

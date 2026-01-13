package com.coolfxin.simplecrypto

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

object FileUtils {

    private const val TAG = "FileUtils"

    /**
     * 从 URI 获取文件名
     */
    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null

        // 对于媒体文件，优先使用 MediaStore 的 DISPLAY_NAME
        if (uri.scheme == "content") {
            when {
                // Android 13+ Photo Picker URI: content://media/picker_get_content/...
                uri.toString().contains("picker_get_content") -> {
                    Log.d(TAG, "Detected Photo Picker URI: $uri")
                    // 从 URI 中提取 media ID
                    val mediaId = uri.lastPathSegment
                    if (mediaId != null) {
                        // 尝试从 Images 和 Video 中查找
                        val projection = arrayOf(
                            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                            android.provider.MediaStore.MediaColumns.MIME_TYPE
                        )

                        // 先尝试图片
                        context.contentResolver.query(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            "${android.provider.MediaStore.MediaColumns._ID} = ?",
                            arrayOf(mediaId),
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                                if (index != -1) {
                                    result = cursor.getString(index)
                                    Log.d(TAG, "Got filename from Images: $result")
                                }
                            }
                        }

                        // 如果图片没找到，尝试视频
                        if (result == null) {
                            context.contentResolver.query(
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                projection,
                                "${android.provider.MediaStore.MediaColumns._ID} = ?",
                                arrayOf(mediaId),
                                null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                                    if (index != -1) {
                                        result = cursor.getString(index)
                                        Log.d(TAG, "Got filename from Videos: $result")
                                    }
                                }
                            }
                        }
                    }
                }
                uri.authority == "media" -> {
                    // 直接查询 MediaStore
                    val projection = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                            if (index != -1) {
                                result = cursor.getString(index)
                                Log.d(TAG, "Got filename from MediaStore: $result")
                            }
                        }
                    }
                }
                uri.authority == "com.android.providers.media.documents" -> {
                    // Media Documents Provider (Android 10+)
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val mediaType = split[0]
                        val mediaId = split[1]

                        val contentUri = when (mediaType) {
                            "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> null
                        }

                        if (contentUri != null) {
                            val projection = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                            val selection = "${android.provider.MediaStore.MediaColumns._ID} = ?"
                            val selectionArgs = arrayOf(mediaId)

                            context.contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                                    if (index != -1) {
                                        result = cursor.getString(index)
                                        Log.d(TAG, "Got filename from Media Documents: $result")
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // 其他 Content Provider（如 Downloads），使用 OpenableColumns
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                result = cursor.getString(nameIndex)
                                Log.d(TAG, "Got filename from OpenableColumns: $result")
                            }
                        }
                    }
                }
            }
        }

        // 如果以上都失败，尝试从 URI path 获取
        if (result == null) {
            result = uri.lastPathSegment
            Log.d(TAG, "Got filename from lastPathSegment: $result")
        }

        Log.d(TAG, "Final filename: $result for URI: $uri")
        return result
    }

    /**
     * 获取文件的原始路径（对于可访问的文件）
     */
    fun getOriginalFilePath(context: Context, uri: Uri): String? {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    return "${context.getExternalFilesDir(null)?.absolutePath}/${
                        split.getOrElse(1) { "" }
                    }"
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = Uri.parse("content://downloads/public_downloads/$id")
                return getDataColumn(context, contentUri, null, null)
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]

                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split.getOrElse(1) { "" })

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        }
        // MediaStore (and general)
        else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        }
        // File
        else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }

        return null
    }

    /**
     * 检查文件是否可访问
     */
    fun isFileAccessible(filePath: String?): Boolean {
        if (filePath == null) return false
        try {
            val file = File(filePath)
            return file.exists() && file.canRead()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking file accessibility: $filePath", e)
            return false
        }
    }

    /**
     * 将 URI 复制到缓存目录
     */
    fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // 使用缓存目录
            val cacheDir = File(context.cacheDir, "crypto_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // 创建临时文件
            val fileName = getFileName(context, uri) ?: "temp_${System.currentTimeMillis()}"
            val tempFile = File(cacheDir, "temp_${System.currentTimeMillis()}_$fileName")

            FileOutputStream(tempFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }

            inputStream.close()
            Log.d(TAG, "File copied to cache: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying URI to cache", e)
            null
        }
    }

    /**
     * 获取加密文件存储目录
     */
    fun getEncryptedFilesDir(context: Context): File {
        // 使用应用外部文件目录中的 encrypted_files 子目录
        val encryptedDir = File(context.getExternalFilesDir(null), "encrypted_files")
        if (!encryptedDir.exists()) {
            encryptedDir.mkdirs()
        }
        return encryptedDir
    }

    /**
     * 删除文件
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) {
                    // 如果删除失败，尝试在退出时删除
                    file.deleteOnExit()
                }
                Log.d(TAG, "Delete file ${file.absolutePath}: $deleted")
                deleted
            } else {
                Log.w(TAG, "File does not exist: ${file.absolutePath}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * 通过 MediaStore 删除原始文件（适用于图片和视频）
     * 对于 Android 10+ 的 Scoped Storage，这是更可靠的删除方式
     */
    fun deleteMediaFile(context: Context, uri: Uri): Boolean {
        return try {
            // 检查是否是 Android 13+ Photo Picker URI
            if (uri.toString().contains("picker_get_content")) {
                Log.d(TAG, "Detected Photo Picker URI, converting for deletion")

                // 获取文件名
                val fileName = getFileName(context, uri)
                if (fileName != null) {
                    Log.d(TAG, "Deleting Photo Picker file by name: $fileName")

                    // 先尝试通过文件名删除（最可靠）
                    var deleted = try {
                        context.contentResolver.delete(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                            arrayOf(fileName)
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete from Images by name: ${e.message}")
                        0
                    }
                    if (deleted > 0) {
                        Log.d(TAG, "Successfully deleted from Images by name: $fileName")
                        return true
                    }

                    // 尝试视频
                    deleted = try {
                        context.contentResolver.delete(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                            arrayOf(fileName)
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete from Videos by name: ${e.message}")
                        0
                    }
                    if (deleted > 0) {
                        Log.d(TAG, "Successfully deleted from Videos by name: $fileName")
                        return true
                    }

                    // 如果文件名失败，尝试通过路径查找
                    Log.d(TAG, "Trying to find and delete by querying MediaStore")
                    deleted = try {
                        // 查询 Images 表，找到匹配的记录
                        val projection = arrayOf(
                            MediaStore.Images.Media._ID,
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.DATA
                        )
                        context.contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                            arrayOf(fileName),
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                                val id = cursor.getLong(idIndex)
                                Log.d(TAG, "Found Image with ID: $id, name: $fileName")

                                // 使用找到的 ID 删除
                                context.contentResolver.delete(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    "${MediaStore.Images.Media._ID} = ?",
                                    arrayOf(id.toString())
                                )
                            } else {
                                0
                            }
                        } ?: 0
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to query and delete from Images: ${e.message}")
                        0
                    }

                    if (deleted > 0) {
                        Log.d(TAG, "Successfully deleted by querying Images")
                        return true
                    }

                    // 尝试视频表
                    deleted = try {
                        val projection = arrayOf(
                            MediaStore.Video.Media._ID,
                            MediaStore.Video.Media.DISPLAY_NAME
                        )
                        context.contentResolver.query(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                            arrayOf(fileName),
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                                val id = cursor.getLong(idIndex)
                                Log.d(TAG, "Found Video with ID: $id, name: $fileName")

                                context.contentResolver.delete(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    "${MediaStore.Video.Media._ID} = ?",
                                    arrayOf(id.toString())
                                )
                            } else {
                                0
                            }
                        } ?: 0
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to query and delete from Videos: ${e.message}")
                        0
                    }

                    if (deleted > 0) {
                        Log.d(TAG, "Successfully deleted by querying Videos")
                        return true
                    }

                    Log.w(TAG, "All deletion methods failed for: $fileName")
                    return false
                }
            }

            // 尝试直接删除（最可靠的方式）
            var deleted = try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "Direct delete failed: ${e.message}")
                0
            }
            Log.d(TAG, "Direct delete result: $deleted")

            if (deleted > 0) {
                Log.d(TAG, "Successfully deleted media file using URI: $uri")
                return true
            }

            // 如果直接删除失败，尝试通过 URI 的 _id 在 MediaStore 中精确查找
            val fileName = getFileName(context, uri)
            val mimeType = context.contentResolver.getType(uri)
            Log.d(TAG, "Attempting to delete - URI: $uri, Name: $fileName, MIME: $mimeType")

            // 获取 MediaStore 中的 _id
            val idColumn = "_id"
            val projection = arrayOf(idColumn)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndexOrThrow(idColumn)
                    val id = cursor.getLong(idIndex)

                    // 使用精确的 _id 删除
                    when {
                        mimeType?.startsWith("image/") == true -> {
                            val selection = "${MediaStore.Images.Media._ID} = ?"
                            val selectionArgs = arrayOf(id.toString())
                            deleted = context.contentResolver.delete(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                selection,
                                selectionArgs
                            )
                            Log.d(TAG, "MediaStore Images delete by ID result: $deleted")
                            deleted > 0
                        }
                        mimeType?.startsWith("video/") == true -> {
                            val selection = "${MediaStore.Video.Media._ID} = ?"
                            val selectionArgs = arrayOf(id.toString())
                            deleted = context.contentResolver.delete(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                selection,
                                selectionArgs
                            )
                            Log.d(TAG, "MediaStore Video delete by ID result: $deleted")
                            deleted > 0
                        }
                        else -> {
                            Log.w(TAG, "Unsupported MIME type: $mimeType")
                            false
                        }
                    }
                } else {
                    // 如果查询不到 _id，尝试通过文件名（不推荐，但作为备选方案）
                    Log.w(TAG, "Cannot get ID from URI, trying by name (less reliable)")
                    when {
                        mimeType?.startsWith("image/") == true -> {
                            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                            val selectionArgs = arrayOf(fileName)
                            deleted = context.contentResolver.delete(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                selection,
                                selectionArgs
                            )
                            Log.d(TAG, "MediaStore Images delete by name result: $deleted")
                            deleted > 0
                        }
                        mimeType?.startsWith("video/") == true -> {
                            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
                            val selectionArgs = arrayOf(fileName)
                            deleted = context.contentResolver.delete(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                selection,
                                selectionArgs
                            )
                            Log.d(TAG, "MediaStore Video delete by name result: $deleted")
                            deleted > 0
                        }
                        else -> {
                            Log.w(TAG, "Unsupported MIME type: $mimeType")
                            false
                        }
                    }
                }
            } ?: run {
                Log.e(TAG, "Query returned null cursor")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting media file", e)
            false
        }
    }

    /**
     * 获取文件大小的可读格式
     */
    fun getFileSize(file: File): String {
        val size = file.length()
        if (size <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()

        return DecimalFormat("#,##0.#").format(
            size / Math.pow(1024.0, digitGroups.toDouble())
        ) + " " + units[digitGroups]
    }

    // 辅助方法
    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            cursor = uri?.let {
                context.contentResolver.query(
                    it, projection, selection, selectionArgs, null
                )
            }

            if (cursor != null && cursor.moveToFirst()) {
                val column_index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(column_index)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting data column", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }
}

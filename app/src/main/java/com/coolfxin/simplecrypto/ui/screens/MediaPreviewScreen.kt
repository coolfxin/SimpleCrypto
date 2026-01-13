package com.coolfxin.simplecrypto.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.coolfxin.simplecrypto.FileEncryptionViewModel
import com.coolfxin.simplecrypto.auth.AuthViewModel
import java.io.File

/**
 * 媒体预览界面 - 用于查看已加密的图片和视频
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewScreen(
    encryptedFile: File,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var decryptedFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVideo by remember { mutableStateOf(false) }

    // 解密文件
    LaunchedEffect(encryptedFile) {
        isLoading = true
        errorMessage = null

        val password = authViewModel.getEncryptionPassword()
        if (password == null) {
            errorMessage = "加密密码未配置"
            isLoading = false
            return@LaunchedEffect
        }

        try {
            // 创建临时解密文件
            val tempFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}_${encryptedFile.name.removeSuffix(".enc")}")
            val success = com.coolfxin.simplecrypto.CryptoUtils.decryptFile(password, encryptedFile, tempFile)

            if (success) {
                decryptedFile = tempFile
                // 检查是否为视频
                isVideo = tempFile.extension.lowercase() in listOf("mp4", "mov", "avi", "mkv", "webm", "3gp")
            } else {
                errorMessage = "解密失败，密码可能错误"
            }
        } catch (e: Exception) {
            errorMessage = "解密失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // 清理临时文件
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY || event == Lifecycle.Event.ON_PAUSE) {
                decryptedFile?.let { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            decryptedFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(encryptedFile.name.removeSuffix(".enc")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在解密...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                decryptedFile != null -> {
                    if (isVideo) {
                        VideoPlayer(file = decryptedFile!!)
                    } else {
                        ImageViewer(file = decryptedFile!!)
                    }
                }
            }
        }
    }
}

/**
 * 图片查看器
 */
@Composable
fun ImageViewer(file: File) {
    var showUI by remember { mutableStateOf(false) }

    AsyncImage(
        model = file,
        contentDescription = "预览图片",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showUI = !showUI }
                )
            }
    )
}

/**
 * 视频播放器
 */
@Composable
fun VideoPlayer(file: File) {
    val context = LocalContext.current

    // 使用 remember 创建 ExoPlayer，避免重复创建
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
            // 硬件解码是默认启用的
        }
    }

    DisposableEffect(file) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                controllerAutoShow = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            // 使用 update 参数确保 player 正确设置到视图
            if (playerView.player !== exoPlayer) {
                playerView.player = exoPlayer
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

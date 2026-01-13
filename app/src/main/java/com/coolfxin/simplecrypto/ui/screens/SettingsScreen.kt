package com.coolfxin.simplecrypto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.coolfxin.simplecrypto.auth.AuthViewModel
import com.coolfxin.simplecrypto.auth.NavigationRoute
import androidx.compose.ui.platform.LocalContext

/**
 * 设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current

    // 检查指纹可用性
    LaunchedEffect(Unit) {
        authViewModel.checkBiometricAvailability()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { authViewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 设置选项列表
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // 修改登录密码
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "修改登录密码",
                        description = "更改您的登录密码",
                        onClick = {
                            authViewModel.navigateTo(NavigationRoute.CHANGE_LOGIN_PASSWORD)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 修改加密密码
                    SettingsItem(
                        icon = Icons.Default.Password,
                        title = "修改加密密码",
                        description = "更改用于加密/解密文件的密码",
                        onClick = {
                            authViewModel.navigateTo(NavigationRoute.CHANGE_ENCRYPTION_PASSWORD)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 更新密保问题
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "更新密保问题",
                        description = "修改您的密保问题和答案",
                        onClick = {
                            authViewModel.navigateTo(NavigationRoute.UPDATE_SECURITY_QUESTIONS)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 指纹登录开关
                    BiometricToggleItem(
                        authViewModel = authViewModel,
                        context = context
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = {
                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * 指纹登录开关项
 */
@Composable
private fun BiometricToggleItem(
    authViewModel: AuthViewModel,
    context: android.content.Context
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showBiometricPrompt by remember { mutableStateOf(false) }
    var pendingPassword by remember { mutableStateOf("") }

    // 如果指纹不可用，不显示此选项
    if (!authViewModel.isBiometricAvailable) {
        return
    }

    // 监听指纹验证结果，启用指纹登录
    LaunchedEffect(authViewModel.biometricAuthSuccess) {
        if (showBiometricPrompt && authViewModel.biometricAuthSuccess == true) {
            authViewModel.finalizeEnableBiometricLogin(pendingPassword)
            showBiometricPrompt = false
            pendingPassword = ""
        }
    }

    // 监听成功消息，关闭对话框
    LaunchedEffect(authViewModel.successMessage) {
        if (authViewModel.successMessage != null && authViewModel.successMessage!!.contains("指纹")) {
            showPasswordDialog = false
            showBiometricPrompt = false
            authViewModel.clearSuccess()
        }
    }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text("指纹登录") },
        supportingContent = {
            Text(
                if (authViewModel.isBiometricEnabled) {
                    "已启用"
                } else {
                    "使用指纹快速登录"
                }
            )
        },
        trailingContent = {
            Switch(
                checked = authViewModel.isBiometricEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        // 启用指纹需要密码验证
                        showPasswordDialog = true
                    } else {
                        // 禁用指纹
                        authViewModel.disableBiometricLogin()
                    }
                }
            )
        },
        modifier = Modifier.padding(8.dp)
    )

    // 密码验证对话框（启用指纹时）
    if (showPasswordDialog) {
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        val isLoading = authViewModel.isLoading
        val errorMessage = authViewModel.errorMessage

        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                authViewModel.clearError()
            },
            title = { Text("验证密码") },
            text = {
                Column {
                    Text("请输入当前密码以启用指纹登录")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            authViewModel.clearError()
                        },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        isError = errorMessage != null
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        android.util.Log.d("SettingsScreen", "密码验证按钮点击，密码: ${password.take(1)}***")
                        // 先验证密码
                        if (authViewModel.verifyPasswordForBiometric(password)) {
                            android.util.Log.d("SettingsScreen", "密码验证成功，准备显示指纹认证")
                            // 密码正确，保存密码并显示指纹认证
                            pendingPassword = password
                            showPasswordDialog = false
                            showBiometricPrompt = true
                            android.util.Log.d("SettingsScreen", "showBiometricPrompt设置为true")
                        } else {
                            android.util.Log.e("SettingsScreen", "密码验证失败")
                        }
                    },
                    enabled = password.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text("下一步")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        authViewModel.clearError()
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 指纹认证（密码验证成功后）
    if (showBiometricPrompt) {
        android.util.Log.d("SettingsScreen", "showBiometricPrompt为true，准备启动指纹认证")
        val activity = context as? androidx.fragment.app.FragmentActivity
        android.util.Log.d("SettingsScreen", "获取到的activity: $activity")
        LaunchedEffect(Unit) {
            android.util.Log.d("SettingsScreen", "LaunchedEffect触发，准备调用authenticateForEnable")
            if (activity != null) {
                android.util.Log.d("SettingsScreen", "activity不为null，调用authenticateForEnable")
                authViewModel.authenticateForEnable(activity)
            } else {
                android.util.Log.e("SettingsScreen", "activity为null！")
            }
        }
    }
}

package com.coolfxin.simplecrypto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.coolfxin.simplecrypto.auth.AuthViewModel
import com.coolfxin.simplecrypto.auth.SecurityUtils

/**
 * 设置登录密码界面（首次使用）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSetupScreen(
    authViewModel: AuthViewModel,
    onComplete: () -> Unit = {}
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // 监听状态变化
    LaunchedEffect(authViewModel.navigationState) {
        if (authViewModel.navigationState.name == "SECURITY_QUESTIONS_SETUP") {
            onComplete()
        }
    }

    val passwordStrength = remember(password) {
        SecurityUtils.checkPasswordStrength(password)
    }

    val strengthColor = when (passwordStrength) {
        0, 1 -> Color.Red
        2 -> Color(0xFFFFA500) // Orange
        3 -> Color.Yellow
        4 -> Color.Green
        else -> Color.Gray
    }

    val strengthText = when (passwordStrength) {
        0 -> "非常弱"
        1 -> "弱"
        2 -> "中等"
        3 -> "强"
        4 -> "非常强"
        else -> ""
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置密码") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 图标
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 标题
                Text(
                    text = "欢迎使用 SimpleCrypto",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "请设置登录密码以保护您的数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 密码输入框
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
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = authViewModel.errorMessage != null
                )

                // 密码强度指示器
                if (password.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("密码强度:", style = MaterialTheme.typography.bodySmall)
                            Text(strengthText, style = MaterialTheme.typography.bodySmall, color = strengthColor)
                        }
                        // 强度条
                        LinearProgressIndicator(
                            progress = { passwordStrength.toFloat() / 4f },
                            modifier = Modifier.fillMaxWidth(),
                            color = strengthColor
                        )
                    }
                }

                // 确认密码输入框
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        authViewModel.clearError()
                    },
                    label = { Text("确认密码") },
                    singleLine = true,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                // 密码匹配提示
                if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                    Text(
                        text = "两次输入的密码不一致",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 错误消息
                if (authViewModel.errorMessage != null) {
                    Text(
                        text = authViewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 继续按钮
                Button(
                    onClick = {
                        authViewModel.setupPassword(password, confirmPassword)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotBlank() &&
                        confirmPassword.isNotBlank() &&
                        password == confirmPassword &&
                        !authViewModel.isLoading
                ) {
                    if (authViewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("继续")
                    }
                }
            }
        }
    }
}

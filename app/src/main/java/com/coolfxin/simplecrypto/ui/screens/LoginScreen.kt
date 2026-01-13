package com.coolfxin.simplecrypto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.coolfxin.simplecrypto.auth.AuthViewModel

/**
 * 登录界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit = {}
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 监听登录状态
    LaunchedEffect(authViewModel.navigationState) {
        if (authViewModel.navigationState.name == "MAIN_APP" ||
            authViewModel.navigationState.name == "CHANGE_ENCRYPTION_PASSWORD") {
            onLoginSuccess()
        }
    }

    // 监听成功消息
    LaunchedEffect(authViewModel.successMessage) {
        if (authViewModel.successMessage != null) {
            onLoginSuccess()
        }
    }

    // 指纹登录处理
    val handleBiometricLogin = {
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity != null) {
            authViewModel.biometricLogin(activity)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SimpleCrypto") }
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
                    text = "欢迎回来",
                    style = MaterialTheme.typography.headlineMedium
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

                // 错误消息
                if (authViewModel.errorMessage != null) {
                    Text(
                        text = authViewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 登录按钮（密码）
                Button(
                    onClick = { authViewModel.login(password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotBlank() && !authViewModel.isLoading
                ) {
                    if (authViewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("密码登录")
                    }
                }

                // 指纹登录按钮
                if (authViewModel.isBiometricEnabled && authViewModel.isBiometricAvailable && !authViewModel.isKeyInvalid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledIconButton(
                        onClick = handleBiometricLogin,
                        enabled = !authViewModel.isLoading,
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "指纹登录",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 忘记密码
                TextButton(
                    onClick = { authViewModel.navigateTo(com.coolfxin.simplecrypto.auth.NavigationRoute.PASSWORD_RECOVERY) }
                ) {
                    Text("忘记密码？")
                }
            }
        }
    }
}

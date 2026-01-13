package com.coolfxin.simplecrypto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coolfxin.simplecrypto.auth.AuthViewModel

/**
 * 密码找回界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRecoveryScreen(
    authViewModel: AuthViewModel
) {
    val questions = authViewModel.getSecurityQuestions()
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }

    // 监听状态变化
    LaunchedEffect(authViewModel.navigationState) {
        if (authViewModel.navigationState.name == "CHANGE_LOGIN_PASSWORD") {
            // 导航到新密码设置页面
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("密码找回") }
            )
        }
    ) { paddingValues ->
        if (questions.isEmpty()) {
            // 未设置密保问题
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("未设置密保问题")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "验证身份",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "请回答您的密保问题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 问题1
                    Text(
                        text = "问题 1: ${questions[0]}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = answer1,
                        onValueChange = {
                            answer1 = it
                            authViewModel.clearError()
                        },
                        label = { Text("答案") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 问题2
                    Text(
                        text = "问题 2: ${questions[1]}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = answer2,
                        onValueChange = {
                            answer2 = it
                            authViewModel.clearError()
                        },
                        label = { Text("答案") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 错误消息
                    if (authViewModel.errorMessage != null) {
                        Text(
                            text = authViewModel.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        authViewModel.verifySecurityQuestions(listOf(answer1, answer2))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = answer1.isNotBlank() && answer2.isNotBlank() &&
                        !authViewModel.isLoading
                ) {
                    if (authViewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("验证")
                    }
                }

                TextButton(
                    onClick = { authViewModel.navigateTo(com.coolfxin.simplecrypto.auth.NavigationRoute.LOGIN) }
                ) {
                    Text("返回登录")
                }
            }
        }
    }
}

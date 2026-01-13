package com.coolfxin.simplecrypto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coolfxin.simplecrypto.auth.AuthViewModel
import com.coolfxin.simplecrypto.auth.SecurityQuestionAnswer

/**
 * 更新密保问题界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSecurityQuestionsScreen(
    authViewModel: AuthViewModel
) {
    var question1 by remember { mutableStateOf("") }
    var answer1 by remember { mutableStateOf("") }
    var question2 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }

    // 监听成功消息，成功后自动返回
    LaunchedEffect(authViewModel.successMessage) {
        if (authViewModel.successMessage != null) {
            kotlinx.coroutines.delay(1500)
            authViewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更新密保问题") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "更新密保问题",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "设置新的密保问题用于找回密码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 问题1
                OutlinedTextField(
                    value = question1,
                    onValueChange = { question1 = it },
                    label = { Text("问题 1") },
                    placeholder = { Text("例如：您母亲的姓名是？") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = answer1,
                    onValueChange = { answer1 = it },
                    label = { Text("答案 1") },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 问题2
                OutlinedTextField(
                    value = question2,
                    onValueChange = { question2 = it },
                    label = { Text("问题 2") },
                    placeholder = { Text("例如：您第一只宠物的名字是？") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = answer2,
                    onValueChange = { answer2 = it },
                    label = { Text("答案 2") },
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

                // 成功消息
                if (authViewModel.successMessage != null) {
                    Text(
                        text = authViewModel.successMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val questions = listOf(
                        SecurityQuestionAnswer(question1, answer1),
                        SecurityQuestionAnswer(question2, answer2)
                    )
                    authViewModel.updateSecurityQuestions(questions)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = question1.isNotBlank() && answer1.isNotBlank() &&
                    question2.isNotBlank() && answer2.isNotBlank() &&
                    !authViewModel.isLoading
            ) {
                if (authViewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("更新")
                }
            }
        }
    }
}

package com.coolfxin.simplecrypto

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolfxin.simplecrypto.auth.AuthViewModel
import com.coolfxin.simplecrypto.auth.NavigationRoute
import com.coolfxin.simplecrypto.ui.screens.*
import com.coolfxin.simplecrypto.ui.theme.SimpleCryptoTheme

class MainActivity : FragmentActivity() {
    private var errorMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边缘到边缘显示，支持系统级侧滑返回手势
        enableEdgeToEdge()
        try {
            setContent {
                SimpleCryptoTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "App crashed on startup", e)
            errorMessage = e.message
            showFallbackUI()
        }
    }

    private fun showFallbackUI() {
        setContentView(TextView(this).apply {
            text = "应用启动失败: ${errorMessage ?: "未知错误"}"
            setTextColor(android.graphics.Color.RED)
            textSize = 16f
            setPadding(16, 16, 16, 16)
        })
    }
}

/**
 * 带后退处理的页面容器
 * 使用 BackHandler 拦截系统返回手势，确保在应用内返回而不是退出应用
 */
@Composable
fun HandleBackNavigation(
    currentRoute: NavigationRoute,
    authViewModel: AuthViewModel,
    content: @Composable () -> Unit
) {
    // 判断当前页面是否可以返回（不是根页面）
    val canGoBack = when (currentRoute) {
        NavigationRoute.LOGIN,
        NavigationRoute.PASSWORD_SETUP,
        NavigationRoute.MAIN_APP,
        NavigationRoute.SECURITY_QUESTIONS_SETUP -> false
        else -> true
    }

    // 使用 BackHandler 拦截系统返回手势
    // 当 canGoBack 为 true 时，拦截返回事件并调用 navigateBack()
    // 当 canGoBack 为 false 时，不拦截，让系统处理（会退出应用）
    BackHandler(enabled = canGoBack) {
        authViewModel.navigateBack()
    }

    content()
}

@Composable
fun AppNavigation() {
    val authViewModel: AuthViewModel = viewModel()
    val currentRoute = authViewModel.navigationState

    // 使用 HandleBackNavigation 包裹所有页面
    HandleBackNavigation(currentRoute, authViewModel) {
        // 根据导航状态渲染不同界面
        when (currentRoute) {
            NavigationRoute.LOGIN -> LoginScreen(authViewModel)

            NavigationRoute.PASSWORD_SETUP -> PasswordSetupScreen(authViewModel)

            NavigationRoute.SECURITY_QUESTIONS_SETUP -> SecurityQuestionsSetupScreen(authViewModel)

            NavigationRoute.MAIN_APP -> FileEncryptionScreen(authViewModel)

            NavigationRoute.SETTINGS -> SettingsScreen(authViewModel)

            NavigationRoute.PASSWORD_RECOVERY -> PasswordRecoveryScreen(authViewModel)

            NavigationRoute.RESET_PASSWORD -> ResetPasswordScreen(authViewModel)

            NavigationRoute.CHANGE_LOGIN_PASSWORD -> ChangeLoginPasswordScreen(authViewModel)

            NavigationRoute.CHANGE_ENCRYPTION_PASSWORD -> ChangeEncryptionPasswordScreen(
                authViewModel = authViewModel,
                isSetup = authViewModel.authState.name == "NEEDS_SECURITY_QUESTIONS"
            )

            NavigationRoute.UPDATE_SECURITY_QUESTIONS -> UpdateSecurityQuestionsScreen(authViewModel)

            NavigationRoute.MEDIA_PREVIEW -> {
                val previewFile = authViewModel.previewFile
                if (previewFile != null) {
                    MediaPreviewScreen(
                        encryptedFile = previewFile,
                        authViewModel = authViewModel,
                        onBack = { authViewModel.navigateBack() }
                    )
                } else {
                    // 如果没有预览文件，返回主界面
                    FileEncryptionScreen(authViewModel)
                }
            }
        }
    }
}

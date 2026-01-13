package com.coolfxin.simplecrypto.auth

import android.app.Application
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coolfxin.simplecrypto.BiometricUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 认证视图模型
 * 管理认证状态和导航
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val authManager = AuthManager(application)

    // 认证状态
    var authState by mutableStateOf(AuthState.CHECKING_FIRST_LAUNCH)
        private set

    // 导航状态
    var navigationState by mutableStateOf(NavigationRoute.LOGIN)
        private set

    // 加载状态
    var isLoading by mutableStateOf(false)
        private set

    // 错误消息
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // 成功消息
    var successMessage by mutableStateOf<String?>(null)
        private set

    // 当前登录密码（用于加密/解密加密密码）
    private var currentLoginPassword: String? = null

    // 当前加密密码（内存缓存，用于指纹登录后加解密）
    private var currentEncryptionPassword: String? = null

    // 当前预览的文件
    var previewFile: java.io.File? by mutableStateOf(null)
        private set

    // 指纹登录启用状态
    var isBiometricEnabled by mutableStateOf(false)
        private set

    // 指纹认证是否可用
    var isBiometricAvailable by mutableStateOf(false)
        private set

    // 密钥是否已失效（指纹变化等情况）
    var isKeyInvalid by mutableStateOf(false)
        private set

    // 指纹认证是否成功（用于启用流程）
    var biometricAuthSuccess by mutableStateOf<Boolean?>(null)
        private set

    init {
        checkFirstLaunch()
    }

    /**
     * 检查是否首次启动
     */
    fun checkFirstLaunch() {
        viewModelScope.launch {
            isLoading = true
            try {
                delay(500) // 模拟检查延迟
                val isFirstLaunch = withContext(Dispatchers.IO) {
                    authManager.isFirstLaunch()
                }

                authState = if (isFirstLaunch) {
                    authState = AuthState.FIRST_LAUNCH
                    navigationState = NavigationRoute.PASSWORD_SETUP
                    AuthState.FIRST_LAUNCH
                } else {
                    val authData = withContext(Dispatchers.IO) {
                        authManager.getAuthData()
                    }

                    if (authData?.hasSetupCompleted == true) {
                        // 加载指纹登录状态
                        isBiometricEnabled = authData.biometricEnabled
                        checkBiometricAvailability()

                        authState = AuthState.NOT_AUTHENTICATED
                        navigationState = NavigationRoute.LOGIN
                        AuthState.NOT_AUTHENTICATED
                    } else {
                        authState = AuthState.NEEDS_SECURITY_QUESTIONS
                        navigationState = NavigationRoute.SECURITY_QUESTIONS_SETUP
                        AuthState.NEEDS_SECURITY_QUESTIONS
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查首次启动失败", e)
                errorMessage = "检查应用状态失败"
                authState = AuthState.NOT_AUTHENTICATED
                navigationState = NavigationRoute.LOGIN
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 登录
     */
    fun login(password: String) {
        if (password.isBlank()) {
            errorMessage = "请输入密码"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val isValid = withContext(Dispatchers.IO) {
                    authManager.verifyLoginPassword(password)
                }

                if (isValid) {
                    currentLoginPassword = password
                    authState = AuthState.AUTHENTICATED

                    // 检查是否已设置加密密码
                    val hasEncryptionPassword = withContext(Dispatchers.IO) {
                        authManager.hasEncryptionPassword()
                    }

                    if (!hasEncryptionPassword) {
                        successMessage = "登录成功"
                        navigationState = NavigationRoute.CHANGE_ENCRYPTION_PASSWORD
                    } else {
                        // 有加密密码，直接读取并缓存到内存
                        val encryptionPassword = withContext(Dispatchers.IO) {
                            authManager.getEncryptionPassword()
                        }

                        if (encryptionPassword != null) {
                            currentEncryptionPassword = encryptionPassword
                        }

                        successMessage = "登录成功"
                        navigationState = NavigationRoute.MAIN_APP
                    }
                } else {
                    errorMessage = "密码错误"
                }
            } catch (e: Exception) {
                Log.e(TAG, "登录失败", e)
                errorMessage = "登录失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 设置登录密码
     */
    fun setupPassword(password: String, confirmPassword: String) {
        if (password.length < 6) {
            errorMessage = "密码长度至少为6位"
            return
        }

        if (password != confirmPassword) {
            errorMessage = "两次输入的密码不一致"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val success = withContext(Dispatchers.IO) {
                    authManager.createLoginPassword(password)
                }

                if (success) {
                    currentLoginPassword = password
                    successMessage = "密码设置成功"
                    authState = AuthState.NEEDS_SECURITY_QUESTIONS
                    navigationState = NavigationRoute.SECURITY_QUESTIONS_SETUP
                } else {
                    errorMessage = "设置密码失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置密码失败", e)
                errorMessage = "设置密码失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 设置密保问题
     */
    fun setupSecurityQuestions(questions: List<SecurityQuestionAnswer>) {
        if (questions.size != 2) {
            errorMessage = "请设置2个密保问题"
            return
        }

        if (questions.any { it.question.isBlank() || it.answer.isBlank() }) {
            errorMessage = "请完整填写所有问题和答案"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val securityQuestions = questions.map { qa ->
                    val salt = SecurityUtils.generateSalt()
                    val saltBase64 = SecurityUtils.byteArrayToBase64(salt)
                    val answerHash = SecurityUtils.hashPassword(qa.answer, salt)
                    SecurityQuestion(qa.question, answerHash, saltBase64)
                }

                val success = withContext(Dispatchers.IO) {
                    authManager.saveSecurityQuestions(securityQuestions)
                }

                if (success) {
                    successMessage = "密保问题设置成功"
                    authState = AuthState.NEEDS_SECURITY_QUESTIONS
                    navigationState = NavigationRoute.CHANGE_ENCRYPTION_PASSWORD
                } else {
                    errorMessage = "保存密保问题失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置密保问题失败", e)
                errorMessage = "设置密保问题失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 验证密保问题
     */
    fun verifySecurityQuestions(answers: List<String>) {
        if (answers.size != 2) {
            errorMessage = "请回答所有问题"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val isValid = withContext(Dispatchers.IO) {
                    authManager.verifySecurityQuestions(answers)
                }

                if (isValid) {
                    successMessage = "验证成功"
                    navigationState = NavigationRoute.RESET_PASSWORD
                } else {
                    errorMessage = "答案错误"
                }
            } catch (e: Exception) {
                Log.e(TAG, "验证密保问题失败", e)
                errorMessage = "验证失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 重置登录密码（从密码找回流程）
     */
    fun resetPasswordFromRecovery(newPassword: String, confirmPassword: String) {
        if (newPassword.length < 6) {
            errorMessage = "密码长度至少为6位"
            return
        }

        if (newPassword != confirmPassword) {
            errorMessage = "两次输入的密码不一致"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val success = withContext(Dispatchers.IO) {
                    authManager.resetLoginPassword(newPassword)
                }

                if (success) {
                    successMessage = "密码重置成功"
                    navigationState = NavigationRoute.LOGIN
                } else {
                    errorMessage = "重置密码失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "重置密码失败", e)
                errorMessage = "重置密码失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 重置登录密码
     */
    fun resetPassword(newPassword: String, confirmPassword: String) {
        if (newPassword.length < 6) {
            errorMessage = "密码长度至少为6位"
            return
        }

        if (newPassword != confirmPassword) {
            errorMessage = "两次输入的密码不一致"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val success = withContext(Dispatchers.IO) {
                    authManager.resetLoginPassword(newPassword)
                }

                if (success) {
                    successMessage = "密码重置成功"
                    navigationState = NavigationRoute.LOGIN
                } else {
                    errorMessage = "重置密码失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "重置密码失败", e)
                errorMessage = "重置密码失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 设置/更新加密密码
     */
    fun setEncryptionPassword(password: String, confirmPassword: String) {
        if (password.length < 6) {
            errorMessage = "密码长度至少为6位"
            return
        }

        if (password != confirmPassword) {
            errorMessage = "两次输入的密码不一致"
            return
        }

        val loginPwd = currentLoginPassword
        if (loginPwd == null) {
            errorMessage = "请先登录"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val success = withContext(Dispatchers.IO) {
                    authManager.saveEncryptionPassword(password, loginPwd)
                }

                if (success) {
                    successMessage = "加密密码设置成功"
                    navigationState = NavigationRoute.MAIN_APP
                } else {
                    errorMessage = "设置加密密码失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置加密密码失败", e)
                errorMessage = "设置加密密码失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 修改登录密码
     */
    fun changeLoginPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        if (currentPassword.isBlank()) {
            errorMessage = "请输入当前密码"
            return
        }

        if (newPassword.length < 6) {
            errorMessage = "新密码长度至少为6位"
            return
        }

        if (newPassword != confirmPassword) {
            errorMessage = "两次输入的新密码不一致"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                // 验证当前密码
                val isValid = withContext(Dispatchers.IO) {
                    authManager.verifyLoginPassword(currentPassword)
                }

                if (!isValid) {
                    errorMessage = "当前密码错误"
                    isLoading = false
                    return@launch
                }

                // 更新密码
                val success = withContext(Dispatchers.IO) {
                    authManager.resetLoginPassword(newPassword)
                }

                if (success) {
                    currentLoginPassword = newPassword
                    successMessage = "密码修改成功"
                    navigateBack()
                } else {
                    errorMessage = "修改密码失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "修改密码失败", e)
                errorMessage = "修改密码失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 修改加密密码
     */
    fun changeEncryptionPassword(
        newEncryptionPassword: String,
        confirmPassword: String
    ) {
        if (newEncryptionPassword.length < 6) {
            errorMessage = "密码长度至少为6位"
            return
        }

        if (newEncryptionPassword != confirmPassword) {
            errorMessage = "两次输入的密码不一致"
            return
        }

        val loginPwd = currentLoginPassword
        if (loginPwd == null) {
            errorMessage = "请先登录"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val success = withContext(Dispatchers.IO) {
                    authManager.updateEncryptionPassword(newEncryptionPassword, loginPwd)
                }

                if (success) {
                    successMessage = "加密密码修改成功"
                    navigateBack()
                } else {
                    errorMessage = "修改加密密码失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "修改加密密码失败", e)
                errorMessage = "修改加密密码失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 更新密保问题
     */
    fun updateSecurityQuestions(questions: List<SecurityQuestionAnswer>) {
        if (questions.size != 2) {
            errorMessage = "请设置2个密保问题"
            return
        }

        if (questions.any { it.question.isBlank() || it.answer.isBlank() }) {
            errorMessage = "请完整填写所有问题和答案"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val securityQuestions = questions.map { qa ->
                    val salt = SecurityUtils.generateSalt()
                    val saltBase64 = SecurityUtils.byteArrayToBase64(salt)
                    val answerHash = SecurityUtils.hashPassword(qa.answer, salt)
                    SecurityQuestion(qa.question, answerHash, saltBase64)
                }

                val success = withContext(Dispatchers.IO) {
                    authManager.saveSecurityQuestions(securityQuestions)
                }

                if (success) {
                    successMessage = "密保问题更新成功"
                    navigateBack()
                } else {
                    errorMessage = "更新密保问题失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新密保问题失败", e)
                errorMessage = "更新密保问题失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 获取加密密码
     * 优先使用内存缓存，如果没有则直接读取
     */
    fun getEncryptionPassword(): String? {
        // 优先返回内存中保存的加密密码（指纹登录或密码登录后缓存的）
        if (currentEncryptionPassword != null) {
            return currentEncryptionPassword
        }

        // 如果没有内存缓存，直接读取（因为密码已经明文存储在 EncryptedSharedPreferences 中）
        return authManager.getEncryptionPassword()
    }

    /**
     * 获取密保问题
     */
    fun getSecurityQuestions(): List<String> {
        return authManager.getSecurityQuestions()
    }

    /**
     * 导航到指定页面
     */
    fun navigateTo(route: NavigationRoute) {
        navigationState = route
        clearMessages()
    }

    /**
     * 预览加密文件
     */
    fun previewEncryptedFile(file: java.io.File) {
        previewFile = file
        navigationState = NavigationRoute.MEDIA_PREVIEW
        clearMessages()
    }

    /**
     * 返回上一页
     */
    fun navigateBack() {
        when (navigationState) {
            NavigationRoute.CHANGE_LOGIN_PASSWORD,
            NavigationRoute.CHANGE_ENCRYPTION_PASSWORD,
            NavigationRoute.UPDATE_SECURITY_QUESTIONS -> {
                navigationState = NavigationRoute.SETTINGS
            }
            NavigationRoute.RESET_PASSWORD -> {
                navigationState = NavigationRoute.LOGIN
            }
            NavigationRoute.SETTINGS -> {
                navigationState = NavigationRoute.MAIN_APP
            }
            NavigationRoute.PASSWORD_RECOVERY -> {
                navigationState = NavigationRoute.LOGIN
            }
            NavigationRoute.MEDIA_PREVIEW -> {
                previewFile = null
                navigationState = NavigationRoute.MAIN_APP
            }
            else -> {
                navigationState = NavigationRoute.MAIN_APP
            }
        }
        clearMessages()
    }

    /**
     * 退出登录
     */
    fun logout() {
        currentLoginPassword = null
        authState = AuthState.NOT_AUTHENTICATED
        navigationState = NavigationRoute.LOGIN
        clearMessages()
    }

    /**
     * 清除消息
     */
    fun clearMessages() {
        errorMessage = null
        successMessage = null
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * 清除成功消息
     */
    fun clearSuccess() {
        successMessage = null
    }

    /**
     * 检查生物识别是否可用
     * 并更新 isBiometricAvailable 状态
     */
    fun checkBiometricAvailability() {
        isBiometricAvailable = BiometricUtils.canAuthenticate(getApplication())
    }

    /**
     * 启用指纹登录
     */
    fun enableBiometricLogin(password: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                val success = withContext(Dispatchers.IO) {
                    authManager.setBiometricEnabled(true)
                }

                if (success) {
                    isBiometricEnabled = true
                    successMessage = "指纹登录已启用"
                } else {
                    errorMessage = "启用指纹登录失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "启用指纹登录失败", e)
                errorMessage = "启用指纹登录失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 验证密码（用于启用指纹登录流程）
     */
    fun verifyPasswordForBiometric(password: String): Boolean {
        return if (password.isBlank()) {
            errorMessage = "请输入密码"
            false
        } else {
            val isValid = authManager.verifyLoginPassword(password)
            if (!isValid) {
                errorMessage = "密码错误"
            } else {
                errorMessage = null
            }
            isValid
        }
    }

    /**
     * 执行指纹认证（用于启用流程）
     */
    fun authenticateForEnable(activity: androidx.fragment.app.FragmentActivity) {
        Log.d(TAG, "authenticateForEnable被调用")
        viewModelScope.launch {
            Log.d(TAG, "协程已启动")
            biometricAuthSuccess = null
            try {
                Log.d(TAG, "准备调用BiometricUtils.authenticate")
                val success = BiometricUtils.authenticate(
                    activity = activity,
                    title = "验证指纹",
                    description = "请验证指纹以启用指纹登录"
                )
                Log.d(TAG, "BiometricUtils.authenticate返回: $success")
                biometricAuthSuccess = success

                if (!success) {
                    Log.d(TAG, "指纹验证失败或取消")
                    errorMessage = "指纹验证已取消"
                } else {
                    Log.d(TAG, "指纹验证成功！")
                }
            } catch (e: Exception) {
                Log.e(TAG, "指纹认证失败", e)
                biometricAuthSuccess = false
                when (e) {
                    is KeyPermanentlyInvalidatedException -> {
                        errorMessage = "指纹信息已变化，请重试"
                    }
                    else -> {
                        errorMessage = "指纹认证失败：${e.message}"
                    }
                }
            }
        }
    }

    /**
     * 完成启用指纹登录（指纹认证成功后调用）
     */
    fun finalizeEnableBiometricLogin(password: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                val success = withContext(Dispatchers.IO) {
                    authManager.setBiometricEnabled(true)
                }

                if (success) {
                    isBiometricEnabled = true
                    successMessage = "指纹登录已启用"
                    biometricAuthSuccess = null
                } else {
                    errorMessage = "启用指纹登录失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "启用指纹登录失败", e)
                errorMessage = "启用指纹登录失败：${e.message}"
            } finally {
                isLoading = false
                biometricAuthSuccess = null
            }
        }
    }

    /**
     * 禁用指纹登录
     */
    fun disableBiometricLogin() {
        viewModelScope.launch {
            isLoading = true
            try {
                val success = withContext(Dispatchers.IO) {
                    authManager.setBiometricEnabled(false)
                }

                if (success) {
                    isBiometricEnabled = false
                    successMessage = "指纹登录已禁用"
                } else {
                    errorMessage = "禁用指纹登录失败"
                }
            } catch (e: Exception) {
                Log.e(TAG, "禁用指纹登录失败", e)
                errorMessage = "禁用指纹登录失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 使用指纹登录
     * 需要在 FragmentActivity 中调用
     */
    fun biometricLogin(activity: androidx.fragment.app.FragmentActivity) {
        viewModelScope.launch {
            isLoading = true
            try {
                val success = BiometricUtils.authenticate(
                    activity = activity,
                    title = "指纹登录",
                    description = "请使用指纹进行验证"
                )

                if (success) {
                    performAutoLogin()
                }
            } catch (e: Exception) {
                Log.e(TAG, "指纹登录失败", e)
                when (e) {
                    is KeyPermanentlyInvalidatedException -> {
                        // 密钥已失效（指纹变化等），需要密码验证
                        isKeyInvalid = true
                        errorMessage = "指纹信息已变化，请使用密码登录"
                    }
                    else -> {
                        errorMessage = "指纹登录失败：${e.message}"
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 执行自动登录（指纹认证成功后）
     */
    private fun performAutoLogin() {
        viewModelScope.launch {
            isLoading = true
            try {
                val hasEncryptionPassword = withContext(Dispatchers.IO) {
                    authManager.hasEncryptionPassword()
                }

                authState = AuthState.AUTHENTICATED

                if (!hasEncryptionPassword) {
                    // 没有设置加密密码，直接进入设置加密密码页面
                    navigationState = NavigationRoute.CHANGE_ENCRYPTION_PASSWORD
                    successMessage = "登录成功"
                } else {
                    // 有加密密码，直接读取并缓存（因为密码登录和指纹登录都应该直接能访问）
                    val encryptionPassword = withContext(Dispatchers.IO) {
                        authManager.getEncryptionPassword()
                    }

                    if (encryptionPassword != null) {
                        // 成功获取加密密码，缓存到内存
                        currentEncryptionPassword = encryptionPassword
                        navigationState = NavigationRoute.MAIN_APP
                        successMessage = "登录成功"
                        Log.d(TAG, "指纹登录成功，已缓存加密密码")
                    } else {
                        // 获取失败
                        navigationState = NavigationRoute.MAIN_APP
                        successMessage = "登录成功"
                        Log.w(TAG, "指纹登录成功，但无法获取加密密码")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "自动登录失败", e)
                errorMessage = "登录失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}

package com.coolfxin.simplecrypto.auth

import kotlinx.serialization.Serializable

/**
 * 登录凭证
 */
@Serializable
data class LoginCredentials(
    val passwordHash: String,
    val salt: String
)

/**
 * 密保问题
 */
@Serializable
data class SecurityQuestion(
    val question: String,
    val answerHash: String,
    val salt: String
)

/**
 * 用户认证数据
 */
@Serializable
data class UserAuthData(
    val loginCredentials: LoginCredentials,
    val securityQuestions: List<SecurityQuestion>, // 2个密保问题
    val encryptionPasswordEncrypted: String,  // 加密密码（明文存储在 EncryptedSharedPreferences 中）
    val encryptionPasswordSalt: String,      // 保留用于兼容旧数据
    val hasSetupCompleted: Boolean = false,
    val biometricEnabled: Boolean = false   // 是否启用指纹登录
)

/**
 * 认证状态
 */
enum class AuthState {
    CHECKING_FIRST_LAUNCH,  // 检查是否首次启动
    FIRST_LAUNCH,           // 首次启动，需要设置密码
    NEEDS_PASSWORD_SETUP,   // 需要设置登录密码
    NEEDS_SECURITY_QUESTIONS, // 需要设置密保问题
    AUTHENTICATED,          // 已认证
    NOT_AUTHENTICATED       // 未认证
}

/**
 * 导航路由
 */
enum class NavigationRoute {
    LOGIN,                      // 登录
    PASSWORD_SETUP,             // 设置登录密码
    SECURITY_QUESTIONS_SETUP,   // 设置密保问题
    MAIN_APP,                   // 主应用（文件加密）
    SETTINGS,                   // 设置
    PASSWORD_RECOVERY,          // 密码找回
    RESET_PASSWORD,             // 重置密码（忘记密码后）
    CHANGE_LOGIN_PASSWORD,      // 修改登录密码
    CHANGE_ENCRYPTION_PASSWORD, // 修改加密密码
    UPDATE_SECURITY_QUESTIONS,  // 更新密保问题
    MEDIA_PREVIEW               // 媒体预览
}

/**
 * 密保问题答案输入
 */
data class SecurityQuestionAnswer(
    val question: String,
    val answer: String
)

/**
 * 操作结果
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
    object Loading : AuthResult<Nothing>()
}

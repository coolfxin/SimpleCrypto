package com.coolfxin.simplecrypto.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom

/**
 * 认证数据管理器
 * 使用 EncryptedSharedPreferences 存储认证数据
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_AUTH_DATA = "auth_data"
        private const val KEY_ENCRYPTION_PASSWORD = "encryption_password_hash"
        private const val KEY_ENCRYPTION_PASSWORD_SALT = "encryption_password_salt"
        private const val TAG = "AuthManager"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 检查是否首次启动
     */
    fun isFirstLaunch(): Boolean {
        return !encryptedPrefs.contains(KEY_AUTH_DATA)
    }

    /**
     * 保存用户认证数据
     */
    fun saveAuthData(authData: UserAuthData): Boolean {
        return try {
            val jsonData = json.encodeToString(authData)
            encryptedPrefs.edit()
                .putString(KEY_AUTH_DATA, jsonData)
                .apply()
            Log.d(TAG, "认证数据保存成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存认证数据失败", e)
            false
        }
    }

    /**
     * 获取用户认证数据
     */
    fun getAuthData(): UserAuthData? {
        return try {
            val jsonData = encryptedPrefs.getString(KEY_AUTH_DATA, null)
            if (jsonData != null) {
                json.decodeFromString<UserAuthData>(jsonData)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取认证数据失败", e)
            null
        }
    }

    /**
     * 创建登录密码
     */
    fun createLoginPassword(password: String): Boolean {
        return try {
            val salt = SecurityUtils.generateSalt()
            val saltBase64 = SecurityUtils.byteArrayToBase64(salt)
            val passwordHash = SecurityUtils.hashPassword(password, salt)

            val authData = UserAuthData(
                loginCredentials = LoginCredentials(
                    passwordHash = passwordHash,
                    salt = saltBase64
                ),
                securityQuestions = emptyList(),
                encryptionPasswordEncrypted = "",
                encryptionPasswordSalt = "",
                hasSetupCompleted = false
            )
            saveAuthData(authData)
        } catch (e: Exception) {
            Log.e(TAG, "创建登录密码失败", e)
            false
        }
    }

    /**
     * 验证登录密码
     */
    fun verifyLoginPassword(password: String): Boolean {
        val authData = getAuthData() ?: return false
        val salt = SecurityUtils.base64ToByteArray(authData.loginCredentials.salt)
        return SecurityUtils.verifyPassword(password, authData.loginCredentials.passwordHash, salt)
    }

    /**
     * 保存密保问题
     */
    fun saveSecurityQuestions(questions: List<SecurityQuestion>): Boolean {
        val authData = getAuthData() ?: return false
        val updatedData = authData.copy(
            securityQuestions = questions,
            hasSetupCompleted = true
        )
        return saveAuthData(updatedData)
    }

    /**
     * 验证密保问题答案
     */
    fun verifySecurityQuestions(answers: List<String>): Boolean {
        val authData = getAuthData() ?: return false
        val questions = authData.securityQuestions

        if (questions.size != answers.size) return false

        return questions.zip(answers).all { (question, answer) ->
            val salt = SecurityUtils.base64ToByteArray(question.salt)
            SecurityUtils.verifyPassword(answer, question.answerHash, salt)
        }
    }

    /**
     * 重置登录密码
     */
    fun resetLoginPassword(newPassword: String): Boolean {
        val authData = getAuthData() ?: return false
        val salt = SecurityUtils.generateSalt()
        val saltBase64 = SecurityUtils.byteArrayToBase64(salt)
        val passwordHash = SecurityUtils.hashPassword(newPassword, salt)

        val updatedData = authData.copy(
            loginCredentials = LoginCredentials(
                passwordHash = passwordHash,
                salt = saltBase64
            )
        )
        return saveAuthData(updatedData)
    }

    /**
     * 保存加密密码（直接存储在 EncryptedSharedPreferences 中）
     */
    fun saveEncryptionPassword(encryptionPassword: String, loginPassword: String): Boolean {
        return try {
            // 直接存储加密密码，不再额外加密
            // EncryptedSharedPreferences 本身已经加密存储了
            val authData = getAuthData() ?: return false

            val updatedData = authData.copy(
                encryptionPasswordEncrypted = encryptionPassword
            )

            saveAuthData(updatedData)
        } catch (e: Exception) {
            Log.e(TAG, "保存加密密码失败", e)
            false
        }
    }

    /**
     * 获取加密密码（直接读取）
     */
    fun getEncryptionPassword(): String? {
        val authData = getAuthData() ?: return null

        if (authData.encryptionPasswordEncrypted.isEmpty()) {
            return null
        }

        return authData.encryptionPasswordEncrypted
    }

    /**
     * 更新加密密码
     */
    fun updateEncryptionPassword(newEncryptionPassword: String, loginPassword: String): Boolean {
        return saveEncryptionPassword(newEncryptionPassword, loginPassword)
    }

    /**
     * 获取密保问题
     */
    fun getSecurityQuestions(): List<String> {
        val authData = getAuthData()
        return authData?.securityQuestions?.map { it.question } ?: emptyList()
    }

    /**
     * 检查是否已设置加密密码
     */
    fun hasEncryptionPassword(): Boolean {
        val authData = getAuthData()
        return !authData?.encryptionPasswordEncrypted.isNullOrEmpty()
    }

    /**
     * 设置指纹登录开关
     */
    fun setBiometricEnabled(enabled: Boolean): Boolean {
        val authData = getAuthData() ?: return false
        val updatedData = authData.copy(biometricEnabled = enabled)
        return saveAuthData(updatedData)
    }

    /**
     * 获取指纹登录状态
     */
    fun isBiometricEnabled(): Boolean {
        val authData = getAuthData() ?: return false
        return authData.biometricEnabled
    }

    /**
     * 清除所有数据（用于测试）
     */
    fun clearAllData() {
        encryptedPrefs.edit().clear().apply()
    }
}

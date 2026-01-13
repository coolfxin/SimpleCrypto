package com.coolfxin.simplecrypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.crypto.Cipher

/**
 * 生物识别工具类
 * 用于指纹认证和生物识别登录
 */
object BiometricUtils {

    private const val TAG = "BiometricUtils"

    /**
     * 检查设备是否支持生物识别
     */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )

        return when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.d(TAG, "设备不支持生物识别硬件")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.d(TAG, "生物识别硬件不可用")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d(TAG, "未录入生物识别信息")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Log.d(TAG, "需要安全更新")
                true  // 可以通过生物识别提示用户更新
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.d(TAG, "不支持生物识别")
                false
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                Log.d(TAG, "生物识别状态未知")
                false
            }
            else -> false
        }
    }

    /**
     * 检查是否有录入了生物识别信息（指纹/面部识别等）
     */
    fun hasEnrolledBiometric(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * 显示生物识别提示对话框
     * @param activity 当前 Activity
     * @param title 提示框标题
     * @param description 提示框描述
     * @param onSuccess 认证成功回调
     * @param onError 认证失败回调
     * @param onCanceled 用户取消回调
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "生物识别认证",
        description: String = "请使用指纹或面部识别进行验证",
        negativeButtonText: String = "取消",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCanceled: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "生物识别认证成功")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "生物识别认证失败: errorCode=$errorCode, error=$errString")

                when (errorCode) {
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onError("认证尝试次数过多，请稍后再试")
                    }
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED -> {
                        onCanceled()
                    }
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_TIMEOUT -> {
                        onCanceled()
                    }
                    else -> {
                        onError("认证失败: $errString")
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "生物识别认证失败（指纹不匹配等）")
                // 不调用 onError，因为用户可以重试
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "启动生物识别失败", e)
            onError("启动生物识别失败: ${e.message}")
        }
    }

    /**
     * 挂起函数版本的生物识别认证
     * @return true 表示认证成功，false 表示失败或取消
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "生物识别认证",
        description: String = "请使用指纹或面部识别进行验证",
        negativeButtonText: String = "取消"
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.d(TAG, "生物识别认证成功")
                continuation.resume(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.e(TAG, "生物识别认证错误: errorCode=$errorCode, error=$errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_TIMEOUT -> {
                        continuation.resume(false)
                    }
                    else -> {
                        continuation.resumeWithException(Exception("认证失败: $errString"))
                    }
                }
            }

            override fun onAuthenticationFailed() {
                Log.w(TAG, "生物识别认证失败（可重试）")
                // 不中断协程，用户可以继续尝试
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)

            continuation.invokeOnCancellation {
                try {
                    // 取消认证
                    // biometricPrompt.cancelAuthentication()  // 这个方法不存在
                } catch (e: Exception) {
                    Log.e(TAG, "取消生物识别失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动生物识别失败", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * 挂起函数版本的生物识别认证（支持加密操作）
     * @param cipher 需要生物识别认证的 Cipher
     * @return 认证成功返回 true，失败返回 false
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String = "生物识别认证",
        description: String = "请使用指纹或面部识别进行验证",
        negativeButtonText: String = "取消"
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.d(TAG, "生物识别认证成功（带 Cipher）")
                // 认证成功，cipher 已被授权，可以用于加密/解密操作
                continuation.resume(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.e(TAG, "生物识别认证错误: errorCode=$errorCode, error=$errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_TIMEOUT -> {
                        continuation.resume(false)
                    }
                    else -> {
                        continuation.resumeWithException(Exception("认证失败: $errString"))
                    }
                }
            }

            override fun onAuthenticationFailed() {
                Log.w(TAG, "生物识别认证失败（可重试）")
                // 不中断协程，用户可以继续尝试
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            // 使用 CryptoObject 包装 cipher 进行认证
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            biometricPrompt.authenticate(promptInfo, cryptoObject)

            continuation.invokeOnCancellation {
                try {
                    // 取消认证
                } catch (e: Exception) {
                    Log.e(TAG, "取消生物识别失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动生物识别失败", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * 获取生物识别类型的显示名称
     */
    fun getBiometricName(context: Context): String {
        val packageManager = context.packageManager
        val hasFingerprint = packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
        } else false

        return when {
            hasFingerprint && hasFace -> "指纹或面部识别"
            hasFingerprint -> "指纹识别"
            hasFace -> "面部识别"
            else -> "生物识别"
        }
    }
}

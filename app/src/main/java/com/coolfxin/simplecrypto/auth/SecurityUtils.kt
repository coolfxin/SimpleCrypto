package com.coolfxin.simplecrypto.auth

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 密码安全工具类
 * 提供 PBKDF2WithHmacSHA256 密码哈希功能
 */
object SecurityUtils {
    private const val PBKDF2_ITERATIONS = 100000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    /**
     * 使用 PBKDF2WithHmacSHA256 哈希密码
     */
    fun hashPassword(password: String, salt: ByteArray): String {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        )
        val hash = factory.generateSecret(spec).encoded
        return hash.fold("") { acc, byte -> acc + "%02x".format(byte) }
    }

    /**
     * 生成随机盐值
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * 验证密码
     */
    fun verifyPassword(password: String, hash: String, salt: ByteArray): Boolean {
        val computedHash = hashPassword(password, salt)
        return computedHash.equals(hash, ignoreCase = true)
    }

    /**
     * ByteArray 转 Base64 字符串
     */
    fun byteArrayToBase64(bytes: ByteArray): String {
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    /**
     * Base64 字符串转 ByteArray
     */
    fun base64ToByteArray(base64: String): ByteArray {
        return android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
    }

    /**
     * 验证密码强度
     * @return 0-4 强度等级
     */
    fun checkPasswordStrength(password: String): Int {
        var strength = 0
        if (password.length >= 8) strength++
        if (password.length >= 12) strength++
        if (password.any { it.isDigit() }) strength++
        if (password.any { it.isLetter() }) strength++
        if (password.any { !it.isLetterOrDigit() }) strength++
        return strength.coerceIn(0, 4)
    }
}

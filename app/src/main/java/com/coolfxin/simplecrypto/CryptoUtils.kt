package com.coolfxin.simplecrypto

import android.util.Log
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CryptoUtils {

    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val AES_KEY_SIZE = 256
    private const val PBKDF2_ITERATIONS = 100000
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val CHUNK_SIZE = 256 * 1024 // 256KB chunks

    private fun generateKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            AES_KEY_SIZE
        )
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * 加密文件 - 块链模式，每个块前面写入长度
     * 格式: [SALT] [LENGTH(4)][IV][DATA] [LENGTH(4)][IV][DATA] ...
     */
    fun encryptFile(
        password: String,
        inputFile: File,
        outputFile: File
    ): Boolean {
        if (!inputFile.exists()) {
            Log.e("CryptoUtils", "Input file does not exist: ${inputFile.path}")
            return false
        }

        val fileSize = inputFile.length()
        Log.d("CryptoUtils", "Starting encryption of ${inputFile.name}, size: ${fileSize / 1024 / 1024} MB")

        var inputStream: FileInputStream? = null
        var outputStream: FileOutputStream? = null

        return try {
            val secureRandom = SecureRandom()
            val salt = ByteArray(SALT_LENGTH)
            secureRandom.nextBytes(salt)
            val secretKey = generateKeyFromPassword(password, salt)

            outputStream = FileOutputStream(outputFile)
            outputStream.write(salt)

            inputStream = FileInputStream(inputFile)
            val buffer = ByteArray(CHUNK_SIZE)
            var totalBytesRead = 0L
            var chunkIndex = 0

            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                totalBytesRead += bytesRead

                // 生成 IV
                val iv = ByteArray(IV_LENGTH)
                secureRandom.nextBytes(iv)

                // 加密这个块
                val cipher = Cipher.getInstance(AES_MODE)
                val spec = GCMParameterSpec(TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

                // 加密数据
                val encryptedData = cipher.doFinal(buffer, 0, bytesRead)

                // 写入块结构: [长度(4字节)][IV][加密数据]
                val blockLength = IV_LENGTH + encryptedData.size

                // 写入长度（4 字节，大端序）
                outputStream.write(blockLength shr 24 and 0xFF)
                outputStream.write(blockLength shr 16 and 0xFF)
                outputStream.write(blockLength shr 8 and 0xFF)
                outputStream.write(blockLength and 0xFF)

                // 写入 IV 和加密数据
                outputStream.write(iv)
                outputStream.write(encryptedData)

                // 进度
                chunkIndex++
                if (chunkIndex % 40 == 0 || totalBytesRead % (10 * 1024 * 1024) == 0L) {
                    val progress = (totalBytesRead * 100 / fileSize).toInt()
                    val mbProcessed = totalBytesRead / 1024 / 1024
                    val mbTotal = fileSize / 1024 / 1024
                    Log.d("CryptoUtils", "Encryption progress: $mbProcessed/$mbTotal MB ($progress%)")
                }

                if (chunkIndex % 10 == 0) {
                    outputStream.flush()
                    System.gc()
                }
            }

            outputStream.flush()

            val outputFileSize = outputFile.length()
            Log.d("CryptoUtils", "Encryption completed. Input: ${fileSize / 1024 / 1024} MB, Output: ${outputFileSize / 1024 / 1024} MB")
            true

        } catch (e: OutOfMemoryError) {
            Log.e("CryptoUtils", "Out of memory error during encryption", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Encryption error: ${e.message}", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                Log.e("CryptoUtils", "Error closing input stream", e)
            }
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.e("CryptoUtils", "Error closing output stream", e)
            }
        }
    }

    /**
     * 解密文件 - 读取长度信息来准确解密每个块
     */
    fun decryptFile(
        password: String,
        inputFile: File,
        outputFile: File
    ): Boolean {
        if (!inputFile.exists()) {
            Log.e("CryptoUtils", "Input file does not exist: ${inputFile.path}")
            return false
        }

        val fileSize = inputFile.length()
        Log.d("CryptoUtils", "Starting decryption of ${inputFile.name}, size: ${fileSize / 1024 / 1024} MB")

        var inputStream: FileInputStream? = null
        var outputStream: FileOutputStream? = null

        return try {
            inputStream = FileInputStream(inputFile)

            // 读取 salt
            val salt = ByteArray(SALT_LENGTH)
            var bytesRead = inputStream.read(salt)
            if (bytesRead != SALT_LENGTH) {
                Log.e("CryptoUtils", "Failed to read complete salt: $bytesRead")
                return false
            }

            val secretKey = generateKeyFromPassword(password, salt)
            outputFile.parentFile?.mkdirs()
            outputStream = FileOutputStream(outputFile)

            var totalDataRead = SALT_LENGTH.toLong()
            var chunkIndex = 0

            // 逐块解密
            while (totalDataRead < fileSize) {
                // 读取块长度（4 字节）
                val lengthBytes = ByteArray(4)
                bytesRead = inputStream.read(lengthBytes)
                if (bytesRead != 4) {
                    // 文件结束
                    break
                }

                val blockLength = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
                                 ((lengthBytes[1].toInt() and 0xFF) shl 16) or
                                 ((lengthBytes[2].toInt() and 0xFF) shl 8) or
                                 (lengthBytes[3].toInt() and 0xFF)

                totalDataRead += 4

                if (blockLength <= 0 || blockLength > fileSize - totalDataRead) {
                    Log.e("CryptoUtils", "Invalid block length: $blockLength")
                    break
                }

                // 读取整个块（IV + 加密数据）
                val blockBuffer = ByteArray(blockLength)
                var offset = 0
                while (offset < blockLength) {
                    bytesRead = inputStream.read(blockBuffer, offset, blockLength - offset)
                    if (bytesRead == -1) {
                        Log.e("CryptoUtils", "Unexpected end of file while reading block")
                        break
                    }
                    offset += bytesRead
                }

                if (offset != blockLength) {
                    break
                }

                totalDataRead += blockLength

                // 提取 IV（前 12 字节）
                val iv = blockBuffer.copyOfRange(0, IV_LENGTH)

                // 提取加密数据（剩余部分）
                val encryptedData = blockBuffer.copyOfRange(IV_LENGTH, blockLength)

                // 解密
                try {
                    val cipher = Cipher.getInstance(AES_MODE)
                    val spec = GCMParameterSpec(TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                    val decryptedData = cipher.doFinal(encryptedData)
                    outputStream.write(decryptedData)

                } catch (e: Exception) {
                    Log.e("CryptoUtils", "Error decrypting chunk $chunkIndex: ${e.message}")
                    throw e
                }

                outputStream.flush()

                // 进度
                chunkIndex++
                if (chunkIndex % 40 == 0 || totalDataRead % (10 * 1024 * 1024) == 0L) {
                    val progress = (totalDataRead * 100 / fileSize).toInt()
                    val mbProcessed = totalDataRead / 1024 / 1024
                    val mbTotal = fileSize / 1024 / 1024
                    Log.d("CryptoUtils", "Decryption progress: $mbProcessed/$mbTotal MB ($progress%)")
                }

                if (chunkIndex % 10 == 0) {
                    System.gc()
                }
            }

            outputStream.flush()

            val outputFileSize = outputFile.length()
            Log.d("CryptoUtils", "Decryption completed. Output size: ${outputFileSize / 1024 / 1024} MB")
            true

        } catch (e: OutOfMemoryError) {
            Log.e("CryptoUtils", "Out of memory error during decryption", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Decryption error: ${e.message}", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.e("CryptoUtils", "Error closing output stream", e)
            }
            try {
                inputStream?.close()
            } catch (e: Exception) {
                Log.e("CryptoUtils", "Error closing input stream", e)
            }
        }
    }
}

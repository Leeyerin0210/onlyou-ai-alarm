package com.nemuria.miya.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256/GCM 기반 보이스 파일 암호화/복호화 유틸리티.
 *
 * ## 보호 전략
 * - 암호화 키는 Android Keystore에 저장 → 앱 외부로 추출 불가
 * - 복호화 시 파일이 아닌 ByteArray로 반환 → 디스크에 평문 절대 미생성
 * - AES-GCM은 무결성 검증을 포함하므로, 파일 위/변조 탐지 가능
 */
@Singleton
class VoiceEncryptionUtil @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "miya_voice_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12 // GCM 표준 IV 크기 (바이트)
    }

    /**
     * Android Keystore에서 AES-256 키를 가져오거나, 없으면 새로 생성합니다.
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGen = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    /**
     * [inputStream]의 데이터를 AES-256/GCM으로 암호화하여 [outputFile]에 저장합니다.
     *
     * 파일 구조: [IV (12 bytes)] + [암호화된 데이터 + GCM 태그]
     */
    fun encrypt(inputStream: InputStream, outputFile: File) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val iv = cipher.iv // 자동 생성된 12바이트 IV

        outputFile.outputStream().use { fos ->
            fos.write(iv) // 앞에 IV 저장
            val plainBytes = inputStream.readBytes()
            val encryptedBytes = cipher.doFinal(plainBytes)
            fos.write(encryptedBytes)
        }
    }

    /**
     * [encryptedFile]을 복호화하여 ByteArray로 반환합니다.
     * 디스크에 평문 파일을 절대 생성하지 않습니다.
     *
     * @return 복호화된 오디오 바이트 배열, 실패 시 null
     */
    fun decryptToByteArray(encryptedFile: File): ByteArray? {
        return runCatching {
            val key = getOrCreateKey()
            val fileBytes = encryptedFile.readBytes()

            val iv = fileBytes.copyOfRange(0, IV_SIZE)
            val encryptedData = fileBytes.copyOfRange(IV_SIZE, fileBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                init(Cipher.DECRYPT_MODE, key, gcmSpec)
            }
            cipher.doFinal(encryptedData)
        }.getOrNull()
    }
}

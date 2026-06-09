package com.movit.core.data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
class IosKeychainSecureSessionStore(
    private val service: String = "com.movit.pose.secure_session",
) : SecureSessionStore {

    override fun saveTokens(tokens: SecureAuthTokens) {
        writeKey(KEY_ACCESS_TOKEN, tokens.accessToken)
        writeKey(KEY_REFRESH_TOKEN, tokens.refreshToken)
        writeKey(KEY_EXPIRES_AT, tokens.expiresAtEpochMs.toString())
    }

    override fun readAccessToken(): String? = readKey(KEY_ACCESS_TOKEN)

    override fun readRefreshToken(): String? = readKey(KEY_REFRESH_TOKEN)

    override fun readExpiresAtEpochMs(): Long =
        readKey(KEY_EXPIRES_AT)?.toLongOrNull() ?: 0L

    override fun clearTokens() {
        deleteKey(KEY_ACCESS_TOKEN)
        deleteKey(KEY_REFRESH_TOKEN)
        deleteKey(KEY_EXPIRES_AT)
    }

    private fun writeKey(account: String, value: String) {
        deleteKey(account)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        SecItemAdd(query as CFDictionaryRef, null)
    }

    private fun readKey(account: String): String? {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status != errSecSuccess) return null
            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            NSString.create(data, NSUTF8StringEncoding) as String
        }
    }

    private fun deleteKey(account: String) {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
        )
        SecItemDelete(query as CFDictionaryRef)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "secure_access_token"
        private const val KEY_REFRESH_TOKEN = "secure_refresh_token"
        private const val KEY_EXPIRES_AT = "secure_token_expires_at"
    }
}

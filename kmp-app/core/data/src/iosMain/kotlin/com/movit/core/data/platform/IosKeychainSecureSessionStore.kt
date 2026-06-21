@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.movit.core.data.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
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
        val data = value.toUtf8NSData() ?: return
        memScoped {
            val cfData = CFBridgingRetain(data)
            val query = keychainQuery(
                account,
                kSecValueData to cfData,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
            SecItemAdd(query, null)
            CFBridgingRelease(cfData)
            CFBridgingRelease(query)
        }
    }

    private fun readKey(account: String): String? {
        return memScoped {
            val query = keychainQuery(
                account,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            CFBridgingRelease(query)
            if (status != errSecSuccess) return null
            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            data.toUtf8String()
        }
    }

    private fun deleteKey(account: String) {
        memScoped {
            val query = keychainQuery(account)
            SecItemDelete(query)
            CFBridgingRelease(query)
        }
    }

    private fun MemScope.keychainQuery(
        account: String,
        vararg extra: Pair<CFStringRef?, CFTypeRef?>,
    ): CFDictionaryRef? {
        val cfService = CFBridgingRetain(service)
        val cfAccount = CFBridgingRetain(account)
        val map = buildMap {
            put(kSecClass, kSecClassGenericPassword)
            put(kSecAttrService, cfService)
            put(kSecAttrAccount, cfAccount)
            extra.forEach { (key, value) ->
                if (key != null && value != null) {
                    put(key, value)
                }
            }
        }
        val query = cfDictionaryOf(map)
        CFBridgingRelease(cfService)
        CFBridgingRelease(cfAccount)
        return query
    }

    private fun MemScope.cfDictionaryOf(map: Map<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val keys = allocArrayOf(*map.keys.toTypedArray())
        val values = allocArrayOf(*map.values.toTypedArray())
        return CFDictionaryCreate(
            kCFAllocatorDefault,
            keys.reinterpret(),
            values.reinterpret(),
            map.size.convert(),
            null,
            null,
        )
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "secure_access_token"
        private const val KEY_REFRESH_TOKEN = "secure_refresh_token"
        private const val KEY_EXPIRES_AT = "secure_token_expires_at"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toUtf8NSData(): NSData? =
    encodeToByteArray().let { bytes ->
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toUtf8String(): String? =
    memScoped {
        val length = this@toUtf8String.length.toInt()
        if (length == 0) return ""
        val buffer = ByteArray(length)
        buffer.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length.convert())
        }
        buffer.decodeToString()
    }

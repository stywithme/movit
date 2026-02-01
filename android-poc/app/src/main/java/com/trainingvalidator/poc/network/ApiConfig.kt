package com.trainingvalidator.poc.network

import android.os.Build

/**
 * API Configuration
 * 
 * Central configuration for API endpoints and networking.
 * 
 * For testing on physical device:
 * 1. Find your computer's LAN IP: ipconfig (Windows) or ifconfig (Mac/Linux)
 * 2. Update PHYSICAL_DEVICE_IP below
 * 3. Ensure phone and computer are on the same WiFi network
 * 4. Make sure your backend is running and accessible
 */
object ApiConfig {
    /**
     * Port number for the backend server
     */
    private const val PORT = 3000
    
    /**
     * Emulator localhost address.
     * 10.0.2.2 is Android Emulator's alias for host machine's localhost.
     */
    private const val EMULATOR_HOST = "10.0.2.2"
    
    /**
     * Physical device: Your computer's LAN IP address.
     * 
     * HOW TO FIND YOUR IP:
     * - Windows: Run `ipconfig` in cmd, look for "IPv4 Address" under your WiFi adapter
     * - Mac/Linux: Run `ifconfig` or `ip addr`, look for your WiFi interface
     * 
     * Current: 192.168.68.102 (auto-detected)
     */
    private const val PHYSICAL_DEVICE_IP = "192.168.68.102"
    
    /**
     * Base URL for emulator
     */
    private const val BASE_URL_EMULATOR = "http://$EMULATOR_HOST:$PORT/"
    
    /**
     * Base URL for physical device
     */
    private const val BASE_URL_PHYSICAL = "http://$PHYSICAL_DEVICE_IP:$PORT/"
    
    /**
     * Connection timeout in seconds
     */
    const val CONNECT_TIMEOUT = 30L
    
    /**
     * Read timeout in seconds
     */
    const val READ_TIMEOUT = 60L
    
    /**
     * Write timeout in seconds
     */
    const val WRITE_TIMEOUT = 60L
    
    /**
     * Detect if running on emulator
     */
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("google/sdk_gphone")
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.PRODUCT == "google_sdk"
                || Build.PRODUCT == "sdk_gphone64_arm64"
                || Build.PRODUCT.startsWith("sdk_gphone"))
    }
    
    /**
     * Get the appropriate base URL based on device type.
     * Automatically detects emulator vs physical device.
     */
    fun getBaseUrl(): String {
        return if (isEmulator()) BASE_URL_EMULATOR else BASE_URL_PHYSICAL
    }
    
    /**
     * Force override: Use specific base URL.
     * Set to null to use automatic detection.
     */
    var overrideBaseUrl: String? = null
    
    /**
     * Get the final base URL (considering override).
     */
    fun getEffectiveBaseUrl(): String {
        return overrideBaseUrl ?: getBaseUrl()
    }
}

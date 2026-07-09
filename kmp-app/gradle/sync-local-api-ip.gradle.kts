import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Properties

/**
 * When api.mode=local, set api.physical_device_ip in local.properties to this machine's LAN IP
 * so physical devices can reach the dev backend without hand-editing the file each time.
 *
 * Precedence for api.mode: -Papi.mode > local.properties > api.properties > "local"
 */
fun detectLanIPv4(): String? {
    fun isPrivateLan(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return when {
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 10 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            else -> false
        }
    }

    fun interfacePriority(name: String, displayName: String?): Int = when {
        name.startsWith("wlan", ignoreCase = true) -> 4
        name.startsWith("wi-fi", ignoreCase = true) -> 4
        displayName?.contains("wi-fi", ignoreCase = true) == true -> 4
        name.startsWith("eth", ignoreCase = true) -> 3
        displayName?.contains("ethernet", ignoreCase = true) == true -> 3
        else -> 1
    }

    return NetworkInterface.getNetworkInterfaces().toList()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .sortedByDescending { interfacePriority(it.name, it.displayName) }
        .flatMap { iface ->
            iface.inetAddresses.toList().asSequence().map { addr -> iface to addr }
        }
        .filter { (_, addr) -> addr is Inet4Address && !addr.isLoopbackAddress }
        .mapNotNull { (_, addr) -> (addr as Inet4Address).hostAddress }
        .filter { ip -> !ip.startsWith("169.254.") && isPrivateLan(ip) }
        .firstOrNull()
}

fun readApiMode(rootDir: File): String {
    val cliOverride = settings.providers.gradleProperty("api.mode").orNull
    if (!cliOverride.isNullOrBlank()) return cliOverride.trim()

    val props = Properties()
    rootDir.resolve("api.properties").takeIf { it.isFile }?.inputStream()?.use { props.load(it) }
    rootDir.resolve("local.properties").takeIf { it.isFile }?.inputStream()?.use { props.load(it) }
    return props.getProperty("api.mode", "local").trim()
}

fun upsertPropertyKey(file: File, key: String, value: String) {
    val lines = if (file.isFile) file.readLines(Charsets.UTF_8).toMutableList() else mutableListOf()
    val pattern = Regex("""^\s*${Regex.escape(key)}\s*=""")
    val newLine = "$key=$value"
    val index = lines.indexOfFirst { pattern.containsMatchIn(it) }
    if (index >= 0) {
        lines[index] = newLine
    } else {
        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add(newLine)
    }
    file.parentFile?.mkdirs()
    val text = lines.joinToString("\n")
    file.writeText(if (text.isEmpty()) "" else "$text\n", Charsets.UTF_8)
}

fun syncLocalApiPhysicalIp(rootDir: File) {
    if (readApiMode(rootDir) != "local") return

    val detected = detectLanIPv4()
    if (detected.isNullOrBlank()) {
        logger.lifecycle("[movit] api.mode=local but no LAN IPv4 found; leaving api.physical_device_ip unchanged")
        return
    }

    val localProps = rootDir.resolve("local.properties")
    val props = Properties()
    if (localProps.isFile) localProps.inputStream().use { props.load(it) }
    val current = props.getProperty("api.physical_device_ip")?.trim()

    if (current == detected) return

    upsertPropertyKey(localProps, "api.physical_device_ip", detected)
    logger.lifecycle(
        "[movit] api.mode=local → api.physical_device_ip=$detected" +
            if (current.isNullOrBlank()) "" else " (was $current)",
    )
}

syncLocalApiPhysicalIp(settings.rootDir)

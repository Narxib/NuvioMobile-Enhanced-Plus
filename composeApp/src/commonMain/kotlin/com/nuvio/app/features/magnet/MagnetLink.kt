package com.nuvio.app.features.magnet

data class MagnetLink(
    val uri: String,
    val infoHash: String,
    val displayName: String? = null,
    val trackers: List<String> = emptyList(),
    val exactLength: Long? = null,
) {
    companion object {
        fun parse(raw: String): MagnetLink? {
            val uri = extractFromText(raw) ?: return null
            val queryStart = uri.indexOf('?')
            if (queryStart < 0) return null
            val parameters = parseQuery(uri.substring(queryStart + 1))
            val infoHash = parameters["xt"]
                .orEmpty()
                .asSequence()
                .mapNotNull(::normalizeTopic)
                .firstOrNull()
                ?: return null
            return MagnetLink(
                uri = uri,
                infoHash = infoHash,
                displayName = parameters["dn"].orEmpty().firstOrNull()?.trim()?.takeIf(String::isNotBlank),
                trackers = parameters["tr"].orEmpty()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct(),
                exactLength = parameters["xl"].orEmpty().firstOrNull()?.toLongOrNull(),
            )
        }

        fun extractFromText(text: String): String? {
            val start = text.indexOf("magnet:?", ignoreCase = true)
            if (start < 0) return null
            var end = text.length
            for (index in start until text.length) {
                if (text[index].isWhitespace()) {
                    end = index
                    break
                }
            }
            return text.substring(start, end).trim()
        }
    }
}

private fun parseQuery(query: String): Map<String, List<String>> {
    val parameters = linkedMapOf<String, MutableList<String>>()
    query.split('&').forEach { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0) return@forEach
        val key = decodeComponent(pair.substring(0, separator)).lowercase()
        val value = decodeComponent(pair.substring(separator + 1))
        parameters.getOrPut(key) { mutableListOf() } += value
    }
    return parameters
}

private fun decodeComponent(value: String): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '%' && index + 2 < value.length) {
            val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (decoded != null) {
                bytes += decoded.toByte()
                index += 3
                continue
            }
        }
        val encoding = if (char == '+') " " else char.toString()
        bytes += encoding.encodeToByteArray().toList()
        index++
    }
    return bytes.toByteArray().decodeToString()
}

private fun normalizeTopic(value: String): String? {
    val topic = value.trim()
    val btihPrefix = "urn:btih:"
    if (topic.startsWith(btihPrefix, ignoreCase = true)) {
        return topic.substring(btihPrefix.length).trim().toCanonicalInfoHash()
    }
    val btmhPrefix = "urn:btmh:"
    if (topic.startsWith(btmhPrefix, ignoreCase = true)) {
        return topic.substring(btmhPrefix.length)
            .trim()
            .removePrefix("1220")
            .lowercase()
            .takeIf { it.length == 64 && it.all(Char::isHexDigit) }
    }
    return null
}

private fun String.toCanonicalInfoHash(): String? = when {
    length == 40 && all(Char::isHexDigit) -> lowercase()
    length == 32 -> toBase32Hex()
    else -> null
}

private fun String.toBase32Hex(): String? {
    var buffer = 0
    var bits = 0
    val bytes = mutableListOf<Byte>()
    for (char in uppercase()) {
        val value = BASE32_ALPHABET.indexOf(char)
        if (value < 0) return null
        buffer = (buffer shl 5) or value
        bits += 5
        if (bits >= 8) {
            bits -= 8
            bytes += ((buffer shr bits) and 0xff).toByte()
        }
        buffer = buffer and ((1 shl bits) - 1)
    }
    if (bytes.size != 20) return null
    return bytes.joinToString("") { byte ->
        val value = byte.toInt() and 0xff
        "${HEX_ALPHABET[value ushr 4]}${HEX_ALPHABET[value and 0x0f]}"
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || lowercaseChar() in 'a'..'f'

private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
private const val HEX_ALPHABET = "0123456789abcdef"

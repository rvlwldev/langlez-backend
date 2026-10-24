package com.langlez.attachment

import java.nio.charset.StandardCharsets

const val SIGNATURE_BYTES = 32

private val IMAGE_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1", "avif", "avis")
private val AUDIO_BRANDS = setOf("M4A ", "M4B ", "M4P ")

private fun ByteArray.startsWith(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { (this[it].toInt() and 0xFF) == expected[it] }

private fun ByteArray.hasAscii(offset: Int, text: String): Boolean =
    size >= offset + text.length && text.indices.all { this[offset + it].toInt() == text[it].code }

private fun ByteArray.isoBrand(): String? =
    if (size >= 12 && hasAscii(4, "ftyp")) String(this, 8, 4, StandardCharsets.ISO_8859_1) else null

enum class Signature(val matches: (ByteArray) -> Boolean) {
    JPEG({ it.startsWith(0xFF, 0xD8, 0xFF) }),
    PNG({ it.startsWith(0x89, 0x50, 0x4E, 0x47) }),
    GIF({ it.hasAscii(0, "GIF8") }),
    WEBP({ it.hasAscii(0, "RIFF") && it.hasAscii(8, "WEBP") }),
    WAV({ it.hasAscii(0, "RIFF") && it.hasAscii(8, "WAVE") }),
    ISO_IMAGE({ it.isoBrand() in IMAGE_BRANDS }),
    ISO_VIDEO({ it.isoBrand()?.let { brand -> brand !in IMAGE_BRANDS && brand !in AUDIO_BRANDS } == true }),
    ISO_AUDIO({ it.isoBrand()?.let { brand -> brand !in IMAGE_BRANDS } == true }),
    MATROSKA({ it.startsWith(0x1A, 0x45, 0xDF, 0xA3) }),
    OGG({ it.hasAscii(0, "OggS") }),
    MP3({ it.hasAscii(0, "ID3") || (it.startsWith(0xFF) && it.size > 1 && (it[1].toInt() and 0xE0) == 0xE0 && (it[1].toInt() and 0x06) != 0) }),
    AAC_ADTS({ it.startsWith(0xFF, 0xF1) || it.startsWith(0xFF, 0xF9) }),
    AMR({ it.hasAscii(0, "#!AMR") }),
    CAF({ it.hasAscii(0, "caff") }),
}

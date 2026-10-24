package com.langlez.attachment

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

private fun bytes(vararg parts: Any): ByteArray =
    parts.flatMap { part ->
        when (part) {
            is String -> part.map { it.code.toByte() }
            is Int -> listOf(part.toByte())
            else -> error("지원하지 않는 조각")
        }
    }.toByteArray().copyOf(SIGNATURE_BYTES)

private fun iso(brand: String) = bytes(0, 0, 0, 0x18, "ftyp", brand)

class SignatureTest : BehaviorSpec({

    Given("형식별 앞부분 바이트가 있을 때") {
        Then("각 시그니처는 자기 형식만 알아본다") {
            Signature.JPEG.matches(bytes(0xFF, 0xD8, 0xFF, 0xE0)) shouldBe true
            Signature.JPEG.matches(bytes(0x89, "PNG")) shouldBe false
            Signature.PNG.matches(bytes(0x89, "PNG")) shouldBe true
            Signature.GIF.matches(bytes("GIF89a")) shouldBe true
            Signature.WEBP.matches(bytes("RIFF", 0, 0, 0, 0, "WEBP")) shouldBe true
            Signature.WEBP.matches(bytes("RIFF", 0, 0, 0, 0, "WAVE")) shouldBe false
            Signature.WAV.matches(bytes("RIFF", 0, 0, 0, 0, "WAVE")) shouldBe true
            Signature.MATROSKA.matches(bytes(0x1A, 0x45, 0xDF, 0xA3)) shouldBe true
            Signature.OGG.matches(bytes("OggS")) shouldBe true
            Signature.AMR.matches(bytes("#!AMR\n")) shouldBe true
            Signature.CAF.matches(bytes("caff")) shouldBe true
        }

        Then("MP3 는 ID3 태그나 프레임 동기를 알아보고 AAC ADTS 와 구분한다") {
            Signature.MP3.matches(bytes("ID3")) shouldBe true
            Signature.MP3.matches(bytes(0xFF, 0xFB)) shouldBe true
            Signature.MP3.matches(bytes(0xFF, 0xF1)) shouldBe false
            Signature.AAC_ADTS.matches(bytes(0xFF, 0xF1)) shouldBe true
            Signature.AAC_ADTS.matches(bytes(0xFF, 0xF9)) shouldBe true
            Signature.AAC_ADTS.matches(bytes(0xFF, 0xFB)) shouldBe false
        }

        Then("ISO 미디어는 brand 로 이미지, 영상, 음성을 구분한다") {
            Signature.ISO_IMAGE.matches(iso("heic")) shouldBe true
            Signature.ISO_IMAGE.matches(iso("avif")) shouldBe true
            Signature.ISO_IMAGE.matches(iso("isom")) shouldBe false
            Signature.ISO_VIDEO.matches(iso("isom")) shouldBe true
            Signature.ISO_VIDEO.matches(iso("qt  ")) shouldBe true
            Signature.ISO_VIDEO.matches(iso("M4A ")) shouldBe false
            Signature.ISO_VIDEO.matches(iso("heic")) shouldBe false
            Signature.ISO_AUDIO.matches(iso("M4A ")) shouldBe true
            Signature.ISO_AUDIO.matches(iso("mp42")) shouldBe true
            Signature.ISO_AUDIO.matches(iso("heic")) shouldBe false
            Signature.ISO_VIDEO.matches(bytes("not an iso file")) shouldBe false
        }

        Then("너무 짧은 바이트는 어느 형식으로도 인정하지 않는다") {
            Signature.entries.none { it.matches(ByteArray(0)) } shouldBe true
        }
    }
})

package com.langlez.attachment

import com.langlez.attachment.AttachmentType.AUDIO
import com.langlez.attachment.AttachmentType.IMAGE
import com.langlez.attachment.AttachmentType.VIDEO

enum class ContentFormat(
    val type: AttachmentType,
    val contentType: String,
    val extension: String,
    val signature: Signature,
) {
    IMAGE_JPEG(IMAGE, "image/jpeg", "jpg", Signature.JPEG),
    IMAGE_PNG(IMAGE, "image/png", "png", Signature.PNG),
    IMAGE_GIF(IMAGE, "image/gif", "gif", Signature.GIF),
    IMAGE_WEBP(IMAGE, "image/webp", "webp", Signature.WEBP),
    IMAGE_HEIC(IMAGE, "image/heic", "heic", Signature.ISO_IMAGE),
    IMAGE_HEIF(IMAGE, "image/heif", "heif", Signature.ISO_IMAGE),
    IMAGE_AVIF(IMAGE, "image/avif", "avif", Signature.ISO_IMAGE),

    VIDEO_MP4(VIDEO, "video/mp4", "mp4", Signature.ISO_VIDEO),
    VIDEO_QUICKTIME(VIDEO, "video/quicktime", "mov", Signature.ISO_VIDEO),
    VIDEO_3GPP(VIDEO, "video/3gpp", "3gp", Signature.ISO_VIDEO),
    VIDEO_3GPP2(VIDEO, "video/3gpp2", "3g2", Signature.ISO_VIDEO),
    VIDEO_WEBM(VIDEO, "video/webm", "webm", Signature.MATROSKA),
    VIDEO_M4V(VIDEO, "video/x-m4v", "m4v", Signature.ISO_VIDEO),

    AUDIO_MP4(AUDIO, "audio/mp4", "m4a", Signature.ISO_AUDIO),
    AUDIO_M4A(AUDIO, "audio/m4a", "m4a", Signature.ISO_AUDIO),
    AUDIO_X_M4A(AUDIO, "audio/x-m4a", "m4a", Signature.ISO_AUDIO),
    AUDIO_AAC(AUDIO, "audio/aac", "aac", Signature.AAC_ADTS),
    AUDIO_MPEG(AUDIO, "audio/mpeg", "mp3", Signature.MP3),
    AUDIO_OGG(AUDIO, "audio/ogg", "ogg", Signature.OGG),
    AUDIO_OPUS(AUDIO, "audio/opus", "opus", Signature.OGG),
    AUDIO_WEBM(AUDIO, "audio/webm", "webm", Signature.MATROSKA),
    AUDIO_3GPP(AUDIO, "audio/3gpp", "3gp", Signature.ISO_AUDIO),
    AUDIO_AMR(AUDIO, "audio/amr", "amr", Signature.AMR),
    AUDIO_WAV(AUDIO, "audio/wav", "wav", Signature.WAV),
    AUDIO_X_WAV(AUDIO, "audio/x-wav", "wav", Signature.WAV),
    AUDIO_X_CAF(AUDIO, "audio/x-caf", "caf", Signature.CAF);

    companion object {
        private val byContentType = entries.associateBy { it.contentType }

        fun find(contentType: String): ContentFormat? = byContentType[contentType.trim().lowercase()]
    }
}

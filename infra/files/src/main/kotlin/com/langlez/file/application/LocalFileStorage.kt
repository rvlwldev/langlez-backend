package com.langlez.file.application

import java.io.File
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
@Profile("local")
internal class LocalFileStorage : FileStorage {
    private val rootPath = "attachments"

    override fun upload(file: MultipartFile, folder: String?): String {
        val dir = if (folder.isNullOrBlank()) File(rootPath)
        else File("$rootPath/$folder").apply { if (!this.exists()) this.mkdirs() }

        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        val targetFile = File(dir, fileName)

        file.transferTo(targetFile)

        return if (folder.isNullOrBlank()) "/$rootPath/$fileName"
        else "/$rootPath/$folder/$fileName"
    }

    override fun delete(fileUrl: String) {
        val file = File(fileUrl.removePrefix("/"))
        if (file.exists()) file.delete()
    }
}

package com.langlez.file

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.UUID

@Component
@Profile("local")
internal class LocalFileStorage : FileStorage {
    private val rootPath = "storage"

    override fun upload(file: MultipartFile, folder: String): String {
        val dir = File("$rootPath/$folder")
        if (!dir.exists()) dir.mkdirs()

        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        val targetFile = File(dir, fileName)
        file.transferTo(targetFile)

        return "/$rootPath/$folder/$fileName"
    }

    override fun delete(fileUrl: String) {
        val file = File(fileUrl.removePrefix("/"))
        if (file.exists()) file.delete()
    }
}

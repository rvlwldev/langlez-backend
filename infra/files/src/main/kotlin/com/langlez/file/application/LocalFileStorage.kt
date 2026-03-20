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
        val sanitizedFolder = folder?.replace("..", "")?.replace("\\", "/")?.trim('/')

        val dir = if (sanitizedFolder.isNullOrBlank()) File(rootPath)
        else File("$rootPath/$sanitizedFolder").also {
            require(it.canonicalPath.startsWith(File(rootPath).canonicalPath)) { "잘못된 경로" }
            if (!it.exists()) it.mkdirs()
        }

        val fileName = "${UUID.randomUUID()}_${file.originalFilename?.replace("..", "")}"
        val targetFile = File(dir, fileName)
        file.transferTo(targetFile)

        return if (sanitizedFolder.isNullOrBlank()) "/$rootPath/$fileName"
        else "/$rootPath/$sanitizedFolder/$fileName"
    }

    override fun delete(fileUrl: String) {
        val file = File(fileUrl.removePrefix("/"))
        require(file.canonicalPath.startsWith(File(rootPath).canonicalPath)) { "잘못된 경로" }
        if (file.exists()) file.delete()
    }
}

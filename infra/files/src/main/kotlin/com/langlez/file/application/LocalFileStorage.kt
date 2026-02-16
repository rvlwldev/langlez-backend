package com.langlez.file.application

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
@Profile("local")
internal class LocalFileStorage : FileStorage {
    private val rootPath = "attachments"

    override suspend fun upload(file: MultipartFile, folder: String?): String = withContext(Dispatchers.IO) {
        val dir = if (folder.isNullOrBlank()) File(rootPath)
        else File("$rootPath/$folder").apply { if (!exists()) mkdirs() }

        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        val targetFile = File(dir, fileName)

        file.transferTo(targetFile)

        if (folder.isNullOrBlank()) "/$rootPath/$fileName"
        else "/$rootPath/$folder/$fileName"
    }

    override suspend fun delete(fileUrl: String) {
        withContext(Dispatchers.IO) {
            val file = File(fileUrl.removePrefix("/"))
            if (file.exists()) file.delete()
        }
    }
}

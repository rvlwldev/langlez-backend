package com.langlez.file.application

import org.springframework.web.multipart.MultipartFile

interface FileStorage {
    suspend fun upload(file: MultipartFile, folder: String? = null): String
    suspend fun delete(fileUrl: String)
}

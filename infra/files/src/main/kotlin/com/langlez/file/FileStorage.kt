package com.langlez.file

import org.springframework.web.multipart.MultipartFile

interface FileStorage {
    fun upload(file: MultipartFile, folder: String): String
    fun delete(fileUrl: String)
}

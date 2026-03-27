package com.langlez.core

interface FileStorage {
    fun generateUploadUrl(filename: String, contentType: String, directory: String): String
    fun delete(fileUrl: String)
}

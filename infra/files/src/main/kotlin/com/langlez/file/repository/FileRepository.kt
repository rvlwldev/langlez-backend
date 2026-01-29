package com.langlez.file.repository

import com.langlez.file.domain.File
import org.springframework.data.jpa.repository.JpaRepository

interface FileRepository : JpaRepository<File, Long> {
    fun findByUrl(url: String): File?
}

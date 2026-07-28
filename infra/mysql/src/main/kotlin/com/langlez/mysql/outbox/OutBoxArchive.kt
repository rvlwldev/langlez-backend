package com.langlez.mysql.outbox

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.LocalDate

@MappedSuperclass
abstract class OutBoxArchive(
    val domain: String,
    val date: LocalDate,
    val index: Int,
    val count: Int,
    @Column(columnDefinition = "LONGTEXT") val data: String,
) {
    @Id
    val id: Long = 0
}
package com.langlez.consumer.note

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository

@Document("notes")
data class Note(@Id val id: String? = null, val text: String)

interface NoteRepository : MongoRepository<Note, String> {
    fun findByText(text: String): List<Note>
}

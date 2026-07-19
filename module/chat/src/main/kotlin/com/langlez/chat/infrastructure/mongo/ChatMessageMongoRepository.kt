package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import org.springframework.data.mongodb.repository.MongoRepository

interface ChatMessageMongoRepository : MongoRepository<ChatMessage, String>

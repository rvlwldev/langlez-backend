package com.langlez.core

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MessageConsumer(
    val topics: Array<String>,
    val group: String,
    val semantic: MessageSemantic = MessageSemantic.ALO,
)

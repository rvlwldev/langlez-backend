package com.langlez.core

/** 이 메서드를 [topic]의 컨슈머 그룹([group])으로 등록해 자동으로 메시지를 수신하게 한다. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MessageListener(
    val topic: String,
    val group: String,
)

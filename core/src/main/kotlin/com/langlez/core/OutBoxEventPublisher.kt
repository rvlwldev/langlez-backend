package com.langlez.core

interface OutBoxEventPublisher {
    fun publish(type: String, id: String, name: String, payload: Any?)
}
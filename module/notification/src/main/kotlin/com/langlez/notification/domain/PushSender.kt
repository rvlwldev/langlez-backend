package com.langlez.notification.domain

/**
 * 기기 푸시 전송 포트. 구현은 infrastructure 의 FCM 어댑터다.
 *
 * 실패는 예외로 알린다. 삼킬지 재시도할지는 호출부(application)가 정한다.
 */
interface PushSender {
    fun send(token: String, title: String, body: String, data: Map<String, String>)
}

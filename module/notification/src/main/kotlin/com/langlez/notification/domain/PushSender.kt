package com.langlez.notification.domain

/**
 * 기기 푸시 전송 포트. 구현은 infrastructure 의 FCM 어댑터다.
 *
 * 전체 실패(초기화 실패, 네트워크 단절)는 예외로 알린다. 삼킬지 재시도할지는 호출부(application)가 정한다.
 * 토큰 단위 부분 실패는 예외가 아니라 반환값이다 — 100명 중 3명의 토큰이 죽었다고 나머지 97명까지
 * 예외로 묶어 던지면 호출부가 성공분을 가려낼 방법이 없다.
 */
interface PushSender {
    /** 실패한 토큰을 돌려준다. 어떻게 처리할지는 호출부(application)가 정한다. */
    fun sendAll(tokens: Collection<String>, title: String, body: String, data: Map<String, String>): List<String>
}

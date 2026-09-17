package com.langlez.auth.application

/**
 * 리프레시 세션 저장소 포트.
 *
 * `domain` 이 아니라 `application` 에 둔다 — auth 엔 엔티티가 없다. 회전·기기 바인딩·TTL
 * 규칙은 전부 "저장소에 대한 정책"이지 애그리거트의 불변식이 아니라, 포트의 주인이
 * 유스케이스 계층이다. 이름을 `SessionRepository` 로 하지 않는다 — `{Entity}Repository`
 * 는 엔티티가 있다는 신호인데 auth 엔 매핑할 엔티티가 없다.
 */
interface SessionStore {

    /** 새 세션을 연다. 기존 리프레시 토큰과 기기 바인딩을 덮어쓴다. */
    fun open(memberId: Long, refreshToken: String, deviceId: String?)

    /** 저장된 값이 [from] 일 때만 [to] 로 원자 교체하고 TTL 을 다시 건다. 교체했으면 true */
    fun rotate(memberId: Long, from: String, to: String): Boolean

    fun boundDevice(memberId: Long): String?

    /** [deviceId] 가 null 이면 바인딩을 지운다. */
    fun bindDevice(memberId: Long, deviceId: String?)

    fun close(memberId: Long)
}

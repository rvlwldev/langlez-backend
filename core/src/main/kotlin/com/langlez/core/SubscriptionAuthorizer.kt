package com.langlez.core

/**
 * 실시간 채널(STOMP) 구독 인가.
 *
 * 브로커는 `/topic` 하나를 모든 모듈이 나눠 쓴다. 모듈마다 인터셉터를 하나씩 달고 자기 접두사가
 * 아니면 통과시키는 방식이면 **아무도 책임지지 않는 목적지**가 생긴다 — 어느 접두사에도 안 걸리는
 * `/topic` 아래 별표 두 개짜리 패턴이나, 인터셉터가 아예 없는 `/topic/notification/{id}` 가 그대로 열린다.
 *
 * 그래서 모듈은 "내 것이면 이렇게 판정한다"만 선언하고, 판정자가 하나도 없는 목적지를 거부하는 건
 * 공용 게이트가 한다. 새 모듈이 토픽을 추가하면서 인가를 빠뜨리면 열리는 게 아니라 닫힌다.
 *
 * `core` 규칙대로 프레임워크 타입을 노출하지 않는다. 구현체는 각 도메인 모듈의 `infrastructure` 에 둔다.
 */
interface SubscriptionAuthorizer {

    /** 이 목적지를 자기 것으로 판정할지. 다른 모듈 것과 겹치지 않게 접두사·정규식을 좁게 잡는다. */
    fun supports(destination: String): Boolean

    /** `supports` 가 참인 목적지를 이 회원이 구독해도 되는지. */
    fun authorize(destination: String, memberId: Long): Boolean
}

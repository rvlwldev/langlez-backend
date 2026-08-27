package com.langlez.core

/**
 * 카프카 메시지 중복 처리 방지.
 *
 * 카프카는 at-least-once 다. 컨슈머 예외로 인한 재시도(`DefaultErrorHandler` 가 seek 후 재호출),
 * 리밸런싱, 오프셋 커밋 실패, 아웃박스의 발행 재시도가 전부 같은 레코드를 다시 흘린다.
 * 알림처럼 부수 효과가 사용자에게 그대로 보이는 컨슈머는 그때마다 알림을 두 번 만든다.
 *
 * **프로듀서가 messageId 헤더를 달지 않는다** — `OutBoxProcessor` 는 `ProducerRecord(topic, key, payload)`
 * 로만 발행한다. 그래서 식별자는 페이로드 자체에서 만든다. 대신 페이로드에 그 발생 건을
 * 유일하게 가리키는 값이 반드시 들어 있어야 한다 (`ChatMessageSentEvent.messageId`,
 * `MemberFollowedEvent.followId`). 없으면 같은 내용의 두 사건이 하나로 합쳐진다.
 */
interface MessageDeduplicator {

    /**
     * 이미 처리한 메시지면 `true`. 처음 보는 메시지면 **처리 중으로 표시하고** `false`.
     *
     * 검사와 표시가 한 번의 원자 연산(SETNX)이라 인스턴스가 여러 대여도 하나만 통과한다.
     * 판정 자체가 실패하면(저장소 장애) `false`(통과)로 흘린다 — 구현체 주석 참고.
     */
    fun isDuplicate(topic: String, payload: String): Boolean

    /**
     * [isDuplicate] 가 남긴 표시를 지운다. **처리가 실패했을 때 반드시 부른다.**
     *
     * 표시를 남긴 채 예외를 던지면 컨슈머 재시도와 DLT 재투입이 전부 "중복"으로 걸러져
     * 그 메시지가 영영 처리되지 않는다. 중복을 막으려다 유실을 만드는 쪽이 더 나쁘다.
     *
     * **한계: 이 되돌림은 같은 JVM 에서 `Exception` 이 잡혔을 때만 돈다.** `Error`(OOM 등)나
     * 프로세스 강제 종료(SIGKILL, OOMKilled, 노드 축출)로 죽으면 표시만 남고 오프셋은 커밋되지
     * 않는다. 재기동 후 카프카가 다시 흘려도 "중복"으로 걸러져 그대로 유실된다 — TTL 이 풀릴
     * 때까지. 그레이스풀 셧다운은 리스너가 in-flight 를 기다리므로 정상 배포로는 안 터지고,
     * 강제 종료가 유일한 창구다. 감수한 것이지 못 본 게 아니다.
     *
     * 이 창까지 닫으려면 표시를 **처리 성공 후**로 옮겨야 한다(전형적 idempotent-consumer).
     * 그러면 [release] 자체가 필요 없어지는 대신 리밸런싱 중 겹치는 재배달을 못 막는다.
     */
    fun release(topic: String, payload: String)
}

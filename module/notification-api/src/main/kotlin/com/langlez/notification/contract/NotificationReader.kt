package com.langlez.notification.contract

/**
 * 알림 수신 설정(mute·방해금지) 조회 포트. `module/notification` 이 구현한다.
 *
 * 지금 이 포트를 부르는 소비자가 없다 — 알림 발송은 전부 `notification` 모듈 안(`NotificationService`)
 * 에서 끝나서 지금은 자기 저장소를 직접 쓰는 게 맞다. 그럼에도 미리 만드는 이유는, 다른 모듈이
 * "이 회원이 지금 방해금지 중인지" 같은 걸 알아야 하는 순간(예: 채팅방 배지 표시 정책)이 오면
 * `notification` 모듈을 직접 참조하지 않고 이 계약만으로 물을 수 있어야 하기 때문이다.
 */
interface NotificationReader {
    /** 끈 유형이 없으면 빈 집합(전부 켠 상태). */
    fun mutedTypesOf(memberId: Long): Set<String>

    /** 목록/다건 경로용. 회원 수만큼 단건 조회를 돌면 N+1 이다. */
    fun mutedTypesOf(memberIds: Collection<Long>): Map<Long, Set<String>>

    /** 방해금지 시간대 안이면 true. 설정이 없거나 타임존을 모르면 false(미적용). */
    fun isQuietNow(memberId: Long): Boolean
}

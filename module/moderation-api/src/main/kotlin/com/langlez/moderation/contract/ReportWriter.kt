package com.langlez.moderation.contract

/**
 * 신고 접수. moderation 모듈이 구현한다.
 *
 * **지금 이 포트를 부르는 모듈이 없다.** 그래도 두는 이유는 신고 데이터의 소유자가
 * moderation 이라는 사실을 계약으로 못 박아 두기 위해서다 — 소유가 계약으로 드러나 있지 않으면
 * 다음에 신고가 필요한 모듈이 `reports` 테이블을 직접 읽거나 자기 신고 테이블을 만든다.
 *
 * **chat 은 이 포트를 쓰지 않는다.** chat 은 접수 사실을 `chat-user-reported` 카프카 이벤트로
 * 알리고 moderation 이 그걸 컨슘해 저장한다. 신고는 유실되면 안 되고 응답을 기다릴 필요도 없어
 * 포트 호출보다 아웃박스 경유가 맞다. 이 포트는 **응답을 기다려야 하는 조회/접수**가
 * 생겼을 때를 위한 자리다.
 *
 * [sourceType] 을 문자열로 받는 이유는 `MemberReader.findProfileInfo` 의 성별과 같다 —
 * `Report.SourceType` 을 계약으로 끌어올리면 moderation 도메인의 열거값이 전 모듈의 공용 계약이 된다.
 * 허용값은 `ECHO_POST`, `CHAT_USER` 이고, 모르는 값이면 구현이 거부한다.
 */
interface ReportWriter {

    /**
     * 신고를 접수한다. 같은 신고가 이미 있으면 아무 일도 일어나지 않는다(멱등).
     *
     * 식별자는 (신고자, 출처 종류, 출처 id, 트리거 메시지) 조합이다.
     */
    fun report(
        reporterId: Long,
        reportedUserId: Long,
        sourceType: String,
        sourceId: String,
        reason: String,
        triggerMessageId: String? = null,
    )
}

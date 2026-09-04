package com.langlez.moderation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "reports",
    // 같은 신고를 두 행으로 만들지 않는다. 구성은 ReportRepository.exists 의 조건과 같다.
    // **실제 DDL 은 V7 이 만든다** — 그쪽은 `nulls not distinct` 라 트리거 메시지가 NULL 인
    // 신고(게시글 신고 전부)도 막지만, JPA 의 UniqueConstraint 로는 그 의미를 표현할 방법이 없다.
    // validate 는 유니크·인덱스를 보지 않으니 여기 선언은 문서 역할이다. 이걸로 DDL 을 만들지 마라.
    uniqueConstraints = [
        UniqueConstraint(
            name = "UNQ_REPORT_IDENTITY",
            columnNames = ["reporter_id", "source_type", "source_id", "trigger_message_id"],
        ),
    ],
    indexes = [
        Index(name = "IDX_REPORT_SOURCE", columnList = "source_type, id"),
        Index(name = "IDX_REPORT_REPORTED_USER", columnList = "reported_user_id, id"),
        Index(name = "IDX_REPORT_REPORTER", columnList = "reporter_id, id"),
        // 운영 목록의 기본 질의가 "미처리부터 최신순"이다. 실제 DDL 은 V16 이 만든다.
        Index(name = "IDX_REPORT_STATUS", columnList = "status, id desc"),
    ],
)
class Report(
    val reporterId: Long,
    val reportedUserId: Long,
    @Enumerated(EnumType.STRING) val sourceType: SourceType,
    val sourceId: String, // ECHO_POST면 postId, CHAT_USER면 roomId
    @Column(columnDefinition = "TEXT") val reason: String,
    val triggerMessageId: String? = null, // CHAT_USER 신고에서만 사용, "이 메시지 이후 신고됨"
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: Status = Status.RECEIVED
        private set

    /** 운영자 메모. 사용자에게 나가지 않는다. */
    @Column(columnDefinition = "TEXT")
    var adminNote: String? = null
        private set

    var handledBy: Long? = null
        private set

    var handledAt: Instant? = null
        private set

    /**
     * 운영자가 신고를 처리한다. 상태와 함께 처리자·처리 시각을 남긴다.
     *
     * **[Status.RECEIVED] 로는 되돌릴 수 없다.** 접수 시점을 뜻하는 상태라
     * 처리자·처리 시각이 채워진 행과 의미가 어긋난다.
     *
     * 종결(ACTIONED/DISMISSED) 뒤에 다시 바꾸는 것은 막지 않는다. 오분류를 정정할 길이
     * 없으면 남는 수단이 DB 직접 수정뿐이고, 그게 잘못 종결된 신고가 남는 것보다 나쁘다.
     * 마지막으로 만진 사람이 [handledBy] 에 남는다.
     *
     * [note] 가 null 이면 기존 메모를 지우지 않고 그대로 둔다. null 을 "지움"으로 보면
     * 상태만 바꾸려는 요청이 앞선 운영자의 메모를 날린다.
     */
    fun handle(status: Status, note: String?, actorId: Long, now: Instant = Instant.now()) {
        require(status != Status.RECEIVED) { "report.status.invalid" }

        this.status = status
        note?.let { adminNote = it }
        handledBy = actorId
        handledAt = now
    }

    enum class SourceType { ECHO_POST, CHAT_USER }

    /** RECEIVED 접수됨 / REVIEWING 확인 중 / ACTIONED 조치함 / DISMISSED 조치 안 함 */
    enum class Status { RECEIVED, REVIEWING, ACTIONED, DISMISSED }
}

package com.langlez.chat.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "chat_rooms",
    // 같은 두 사람의 방을 두 행으로 만들지 않는다. 실제 DDL 은 V19 가 만들고,
    // 정규화(member_a < member_b)를 강제하는 CHECK 도 그쪽에 있다 — JPA 로는 표현할 수 없다.
    uniqueConstraints = [UniqueConstraint(name = "UNQ_CHAT_ROOM_PAIR", columnNames = ["member_a", "member_b"])],
)
class ChatRoom(
    /**
     * 방을 이루는 두 회원. **항상 오름차순으로 담는다** — [between] 이 정렬해 준다.
     *
     * 유니크 제약(UNQ_CHAT_ROOM_PAIR)이 (a,b) 와 (b,a) 를 같은 값으로 보게 하려면 정규화가 필요한데,
     * 그걸 호출자마다 하게 두면 한 곳만 빼먹어도 같은 두 사람의 방이 두 개 생긴다.
     * 아래 `init` 이 그 실수를 즉시 막고, DB CHECK(member_a < member_b) 가 다시 확인한다.
     */
    @Column(name = "member_a", nullable = false) val memberA: Long,
    @Column(name = "member_b", nullable = false) val memberB: Long,

    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    // 목록 정렬·미리보기를 위해 방에 비정규화해 둔다. 없으면 방마다 마지막 메시지를 다시 조회해야 한다(N+1).
    @Column(name = "last_message_at") var lastMessageAt: Instant? = null,
    @Column(name = "last_message_preview", length = 200) var lastMessagePreview: String? = null,

    @CreatedDate @Column(name = "created_at") val createdAt: Instant = Instant.now(),
) {
    init {
        // DB 에서 읽어 올릴 때는 JPA 용 no-arg 생성자를 타므로 이 검사가 돌지 않는다. 새로 만들 때만이다.
        require(memberA < memberB) { "chat.room.pair" }
    }

    /**
     * 방의 최근 메시지 프리뷰와 시각을 갱신한다.
     *
     * 단조성 가드: 동시 전송이나 비동기/대사 처리 순서 역전으로 과거 메시지가 나중에 커밋되더라도
     * 방 메타가 과거로 역행해 목록 정렬과 프리뷰를 롤백하지 않도록 [hasNothingNewerThan] 으로 방어한다 (B-05).
     */
    fun onMessage(preview: String, at: Instant) {
        if (!hasNothingNewerThan(at)) return
        lastMessagePreview = preview.take(200)
        lastMessageAt = at
    }

    /**
     * [at] 의 메시지가 아직 방에 반영되지 않았는가. 메시지(Mongo)와 방 메타(Postgres)가
     * 어긋났는지 판정한다. 한 번도 갱신되지 않은 방(null)은 무조건 뒤처진 것이다.
     */
    fun isBehind(at: Instant): Boolean = lastMessageAt?.isBefore(at) ?: true

    /**
     * 방 메타가 [at] 보다 최신인 메시지를 이미 물고 있지는 않은가.
     *
     * 삭제처럼 **이미 반영된 메시지를 다시 쓰는** 갱신을 위한 단조성 가드다. 그런 갱신은
     * "이게 마지막 메시지다"를 확인한 시점과 실제로 쓰는 시점이 떨어져 있고, 그 사이에 새 메시지가
     * 먼저 커밋될 수 있다. 그때 그대로 덮으면 [lastMessageAt] 이 과거로 역행해 목록 정렬
     * (`last_message_at desc`)에서 방이 뒤로 밀리고 프리뷰도 새 메시지 것을 잃는다.
     *
     * **비교는 밀리초로 맞춘다.** 같은 메시지인데도 Mongo 는 밀리초까지만 저장하고(BSON date)
     * Postgres 는 `timestamp(6)` 이라 마이크로초를 남긴다. 그대로 비교하면 방금 그 메시지조차
     * "더 최신"으로 잡혀 갱신이 통째로 스킵된다.
     */
    fun hasNothingNewerThan(at: Instant): Boolean =
        lastMessageAt?.truncatedTo(MILLIS)?.isAfter(at.truncatedTo(MILLIS)) != true

    companion object {
        /** (a,b) 로 부르든 (b,a) 로 부르든 같은 방이 되도록 정렬해 만든다. 방 생성은 항상 이걸로 한다. */
        fun between(a: Long, b: Long) = ChatRoom(memberA = minOf(a, b), memberB = maxOf(a, b))
    }
}

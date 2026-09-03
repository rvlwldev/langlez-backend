package com.langlez.follow.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 테이블 이름은 member_follows 그대로다. 모듈이 갈렸다고 테이블을 옮기면 데이터 마이그레이션이
// 따라붙고 V6 의 인덱스도 다시 만들어야 한다. 이름 정리는 별도 작업이다.
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "member_follows",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_MEMBER_FOLLOW", columnNames = ["follower_id", "followed_id"])],
    // 목록은 양방향 모두 커서 페이징(id 내림차순)이다. 유니크 인덱스엔 id 가 없어 정렬을 못 줘서
    // 두 방향 다 전용 인덱스가 필요하다. 실제 DDL 은 V6 가 만든다 (validate 는 인덱스를 안 본다).
    indexes = [
        Index(name = "IDX_MEMBER_FOLLOW_FOLLOWED", columnList = "followed_id, id DESC"),
        Index(name = "IDX_MEMBER_FOLLOW_FOLLOWER", columnList = "follower_id, id DESC"),
    ]
)
class Follow(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long,

    @Column(name = "follower_id") val followerId: Long,
    @Column(name = "followed_id") val followedId: Long,

    @CreatedDate @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
) {
    // 생성 시점에 막는다. 서비스마다 검사하면 새 호출 경로에서 빠뜨린다.
    // (noarg 플러그인이 만드는 JPA 전용 생성자는 초기화 블록을 타지 않으므로 하이드레이션엔 영향이 없다)
    init {
        require(followerId != followedId) { "social.follow.self" }
    }

    constructor(followerId: Long, followedId: Long) : this(
        id = 0,
        followerId = followerId,
        followedId = followedId,
    )
}

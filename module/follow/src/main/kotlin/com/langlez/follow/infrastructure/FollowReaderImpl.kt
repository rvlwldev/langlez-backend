package com.langlez.follow.infrastructure

import com.langlez.follow.contract.FollowReader
import com.langlez.follow.domain.FollowRepository
import com.langlez.follow.infrastructure.jpa.FollowJpaRepository
import org.springframework.stereotype.Repository

/**
 * follow 가 다른 모듈에 내주는 조회 포트 구현.
 *
 * echo 홈 타임라인과 profile 화면이 쓴다. 구현이 없으면 타임라인이 통째로 503 이 되므로
 * follow 모듈이 반드시 이 빈을 올려야 한다.
 */
@Repository
class FollowReaderImpl(
    private val jpa: FollowJpaRepository,
    private val repo: FollowRepository,
) : FollowReader {

    override fun followingIds(memberId: Long): List<Long> =
        jpa.findAllByFollowerId(memberId).map { it.followedId }

    /**
     * COUNT 두 번이다. 한 문장으로 합치려면 `where followed_id = ? or follower_id = ?` 에
     * 조건부 집계를 걸어야 하는데, 그러면 두 인덱스를 BitmapOr 로 묶은 뒤 CASE 를 계산하려고
     * 행마다 힙을 다시 읽는다. 팔로워가 백만인 회원에서 index-only scan 두 번이 훨씬 싸다.
     * 왕복 한 번 차이는 그 대가를 치를 값이 아니다.
     *
     * **두 숫자는 같은 시점이 아니다.** 이 포트는 곧 gRPC/HTTP 로 나가고, 그때는 호출자가
     * `@Transactional` 로 감싸도 원격 쪽 스냅샷은 묶이지 않는다. 즉 호출자가 스냅샷을
     * 보장할 방법이 없다 — 그러니 "필요하면 호출자가 감싸라"고 적어 두지 않는다.
     * 프로필 표시용 숫자라 그 정도 어긋남은 감수한다. 정확한 값이 필요한 요구가 생기면
     * 두 숫자를 한 응답으로 묶어 내는 포트를 이쪽에서 트랜잭션과 함께 만들어야 한다.
     */
    override fun counts(memberId: Long) = FollowReader.CountInfo(
        followers = repo.countFollowers(memberId),
        followings = repo.countFollowings(memberId),
    )
}

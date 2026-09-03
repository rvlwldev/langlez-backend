package com.langlez.follow.domain

/**
 * 팔로우 저장소 포트.
 *
 * 목록은 전부 커서 페이징이다. 정렬·커서 기준은 `created_at` 이 아니라 행 id 다 —
 * 인스턴스마다 시계가 어긋나면 같은 밀리초에 들어온 행이 페이지 경계에서 겹치거나 사라진다.
 */
interface FollowRepository {

    /** 목록 한 줄. `id` 는 다음 페이지 커서로 쓸 행 id, `memberId` 는 상대 회원 id. */
    data class Edge(val id: Long, val memberId: Long)

    fun save(follow: Follow): Follow
    fun find(followerId: Long, followedId: Long): Follow?
    fun delete(followerId: Long, followedId: Long)

    /** 나를 팔로우한 사람들 */
    fun findFollowers(memberId: Long, size: Int, cursor: Long?): List<Edge>

    /** 내가 팔로우한 사람들 */
    fun findFollowings(memberId: Long, size: Int, cursor: Long?): List<Edge>

    /**
     * 팔로워 수 / 팔로잉 수.
     *
     * 카운터 컬럼으로 비정규화하지 않고 매번 COUNT 한다. **되돌리지 마라.**
     * 차단이 팔로우를 양방향으로 끊는데, 그건 `member-blocked` 를 받은 컨슈머가 지운다 —
     * 증감식 카운터를 두면 그 경로에서 조용히 어긋나고, 어긋난 뒤엔 백필 말고 고칠 방법이 없다.
     * COUNT 는 항상 정확하고 백필이 필요 없다.
     *
     * 두 방향 모두 인덱스만 읽고 끝난다 (V6 의 IDX_MEMBER_FOLLOW_FOLLOWED / IDX_MEMBER_FOLLOW_FOLLOWER).
     */
    fun countFollowers(memberId: Long): Long

    fun countFollowings(memberId: Long): Long
}

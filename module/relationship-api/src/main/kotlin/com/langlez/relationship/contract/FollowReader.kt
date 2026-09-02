package com.langlez.relationship.contract

/**
 * 팔로우 그래프 조회. relationship 모듈이 구현한다.
 *
 * 홈 타임라인은 "내가 팔로우한 사람의 글"이라 팔로우 관계를 알아야 하는데, 그건 relationship 소유다.
 * echo 가 relationship 저장소를 직접 들여다보면 경계가 무너지고, 이벤트로 그래프를 복제하면
 * 같은 데이터를 두 벌 들고 어긋날 여지를 만든다. `BlockReader` 와 같은 방식으로 조회만 포트로 뽑는다.
 */
interface FollowReader {
    /** 내가 팔로우하는 회원 id 목록. 팔로우가 없으면 빈 목록. */
    fun followingIds(memberId: Long): List<Long>

    /**
     * 팔로워 수 / 팔로잉 수. 프로필 화면이 두 숫자를 함께 그려서 한 번에 돌려준다.
     *
     * 조회만 포트로 뽑는다는 이 인터페이스의 원칙 그대로다. 카운트를 이벤트로 흘려 다른 모듈이
     * 자기 카운터를 들면 팔로우 그래프가 두 벌이 되고, 차단으로 팔로우가 끊길 때 조용히 어긋난다.
     */
    fun counts(memberId: Long): CountInfo

    /** 팔로워 수와 팔로잉 수. */
    data class CountInfo(val followers: Long, val followings: Long)
}

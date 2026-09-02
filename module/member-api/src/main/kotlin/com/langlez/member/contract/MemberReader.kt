package com.langlez.member.contract

import java.time.LocalDate
import java.util.Locale

/**
 * 회원 계정 조회. member 모듈이 구현한다.
 *
 * 프로필 화면이 계정 소유 정보(handle·성별·국가·생년월일)를 함께 그려서 한 번에 받아 간다.
 * 필드마다 메서드를 쪼개면 이 포트가 나중에 네트워크가 될 때 화면 하나에 왕복이 여러 번 생긴다.
 *
 * 성별을 enum 이 아니라 `String` 으로 내는 이유: 소비자는 응답에 이름만 실어 보낸다.
 * `Member.Gender` 를 계약으로 끌어올리면 계정 도메인의 열거값이 전 모듈의 공용 계약이 된다.
 *
 * 상태 조회는 원래 `MemberStatusQuery` 로 따로 있었다. 둘 다 member 소유의 단순 조회라
 * 하나로 합쳤다 — 소비자가 포트 두 개를 주입받아야 할 이유가 없었다.
 */
interface MemberReader {

    /** 없는 handle 이면 null */
    fun findIdByHandle(handle: String): Long?

    fun findProfileInfo(memberId: Long): ProfileInfo?

    /** 목록 화면용. 회원 수만큼 단건 조회를 돌면 N+1 이다. 없는 id 는 결과에서 빠진다. */
    fun findProfileInfos(memberIds: Collection<Long>): Map<Long, ProfileInfo>

    /**
     * 회원이 없으면 null. 탈퇴 회원은 행이 남으므로 [Status.WITHDRAWN] 으로 온다.
     *
     * `common` 의 인증 필터가 매 요청 계정 상태를 본다. `common` 은 `module/member` 를 참조할 수
     * 없으므로 `Member.Status` 대신 아래 [Status] 를 쓴다.
     *
     * `Boolean` 하나로 줄이지 않는 이유: 정지와 탈퇴는 사용자에게 다른 문구를 내야 한다
     * (`member.suspended` / `member.withdrawn`). 참·거짓만 돌려주면 그 구분이 사라진다.
     */
    fun findStatus(memberId: Long): Status?

    data class ProfileInfo(
        val id: Long,
        val handle: String,
        val nickname: String? = null,
        val gender: String,
        val locale: Locale?,
        val birthDay: LocalDate?,
        // 팔로워/차단 목록이 handle 옆에 프로필 사진을 그린다. 필드 하나 때문에 포트를 또 만들면
        // 이 계약이 네트워크가 될 때 화면 하나에 왕복이 두 번이 된다.
        val imageUrl: String? = null,
    )

    /**
     * `Member.Status` 의 거울.
     *
     * 구현체가 `when` 으로 하나씩 매핑하니 원본에 값이 늘면 컴파일이 깨진다.
     * 이름을 그대로 옮기지 않고 매핑을 두는 건, 원본이 바뀌었는데 여기가 안 바뀐 상태로
     * 조용히 통과하는 걸 막기 위해서다.
     */
    enum class Status { CREATED, ACTIVE, SUSPENDED, WITHDRAWN }
}

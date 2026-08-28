package com.langlez.core

import java.time.LocalDate
import java.util.Locale

/**
 * 회원 계정 조회. member 모듈이 구현한다.
 *
 * 프로필 화면이 계정 소유 정보(handle·성별·국가·생년월일)를 함께 그려서 한 번에 받아 간다.
 * 필드마다 메서드를 쪼개면 이 포트가 나중에 네트워크가 될 때 화면 하나에 왕복이 여러 번 생긴다.
 *
 * 성별을 enum 이 아니라 `String` 으로 내는 이유: 소비자는 응답에 이름만 실어 보낸다.
 * `Member.Gender` 를 core 로 끌어올리면 계정 도메인의 열거값이 전 모듈의 공용 계약이 된다.
 */
interface MemberQuery {

    /** 없는 handle 이면 null */
    fun findIdByHandle(handle: String): Long?

    fun findProfileInfo(memberId: Long): ProfileInfo?

    /** 목록 화면용. 회원 수만큼 단건 조회를 돌면 N+1 이다. 없는 id 는 결과에서 빠진다. */
    fun findProfileInfos(memberIds: Collection<Long>): Map<Long, ProfileInfo>

    data class ProfileInfo(
        val id: Long,
        val handle: String,
        val gender: String,
        val locale: Locale?,
        val birthDay: LocalDate?,
    )
}

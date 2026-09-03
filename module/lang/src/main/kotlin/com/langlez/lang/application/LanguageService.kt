package com.langlez.lang.application

import com.langlez.exception.LanglezException
import com.langlez.lang.api.request.LangReplaceLanguagesRequest
import com.langlez.lang.domain.MemberLanguage
import com.langlez.lang.domain.MemberLanguage.Companion.MAX_LEARNING
import com.langlez.lang.domain.MemberLanguage.Companion.MAX_NATIVE
import com.langlez.lang.domain.MemberLanguage.Role
import com.langlez.lang.domain.MemberLanguageRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LanguageService(private val repo: MemberLanguageRepository) {

    @Transactional(readOnly = true)
    fun findAll(memberId: Long): List<MemberLanguage> = repo.findAll(memberId)

    /**
     * 언어 프로필 전체 교체.
     *
     * **부분 수정(개별 추가·삭제) 엔드포인트를 만들지 않는다.** 상한(모국어 3 / 학습언어 5)·중복·
     * 모국어↔학습 충돌 검사가 목록 전체를 봐야 성립하는데, 개별 수정이면 그 검사가 요청마다 흩어져
     * "마지막 한 건만 통과시키면 상한을 넘길 수 있는" 창이 열린다. 여기서 한 트랜잭션에 다 본다.
     *
     * 삭제와 재삽입을 한 트랜잭션에 묶는 것이 핵심이다. 나뉘면 재삽입이 실패했을 때
     * 언어가 통째로 사라진 상태로 남는다.
     */
    @Transactional
    fun replace(memberId: Long, request: LangReplaceLanguagesRequest): List<MemberLanguage> {
        val languages = request.languages.map { item -> toEntity(memberId, item) }

        // 유니크 제약이 (member_id, language) 라 DB 도 막지만, 그때는 500 이 나간다.
        // 같은 언어를 NATIVE 와 LEARNING 으로 동시에 보내는 요청도 여기서 걸린다.
        if (languages.distinctBy(MemberLanguage::language).size != languages.size)
            throw LanglezException(HttpStatus.BAD_REQUEST, "lang.duplicated")

        val (natives, learnings) = languages.partition { it.role == Role.NATIVE }
        if (natives.size > MAX_NATIVE) throw LanglezException(HttpStatus.BAD_REQUEST, "lang.native.limit-exceeded")
        if (learnings.size > MAX_LEARNING) throw LanglezException(HttpStatus.BAD_REQUEST, "lang.learning.limit-exceeded")

        repo.deleteAll(memberId)
        return repo.saveAll(languages)
    }

    private fun toEntity(memberId: Long, item: LangReplaceLanguagesRequest.Item) = try {
        MemberLanguage(
            memberId = memberId,
            language = item.language,
            role = item.role,
            level = item.level,
        )
    } catch (e: IllegalArgumentException) {
        // 도메인의 IllegalArgumentException 을 그대로 흘리면 500 이 된다. 메시지는 i18n 키다.
        throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
    }
}

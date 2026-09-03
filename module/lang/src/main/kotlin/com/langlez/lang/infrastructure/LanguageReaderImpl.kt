package com.langlez.lang.infrastructure

import com.langlez.lang.contract.LanguageReader
import com.langlez.lang.domain.MemberLanguage
import com.langlez.lang.domain.MemberLanguageRepository
import org.springframework.stereotype.Repository

/**
 * lang 이 다른 모듈에 내주는 조회 포트 구현.
 *
 * matching 이 언어를 보는 유일한 통로다. 구현이 없으면 추천이 통째로 죽으므로
 * lang 모듈이 반드시 이 빈을 올려야 한다.
 */
@Repository
class LanguageReaderImpl(private val repo: MemberLanguageRepository) : LanguageReader {

    override fun languagesOf(memberId: Long): List<LanguageReader.LanguageInfo> =
        repo.findAll(memberId).map(::toInfo)

    override fun languagesOf(memberIds: Collection<Long>): Map<Long, List<LanguageReader.LanguageInfo>> =
        repo.findAll(memberIds)
            .groupBy(MemberLanguage::memberId) { toInfo(it) }

    override fun complementaryCandidates(
        myNativeLanguages: Collection<String>,
        myLearningLanguages: Collection<String>,
        excludeMemberId: Long,
        limit: Int,
    ): List<Long> = repo.findComplementaryCandidates(
        myNativeLanguages = myNativeLanguages,
        myLearningLanguages = myLearningLanguages,
        excludeMemberId = excludeMemberId,
        limit = limit,
    )

    // 이름을 그대로 옮기지 않고 when 으로 매핑한다. 원본 enum 에 값이 늘면 여기서 컴파일이 깨져야
    // 계약에 반영을 빠뜨리지 않는다. (MemberReader.Status 와 같은 이유)
    private fun toInfo(entity: MemberLanguage) = LanguageReader.LanguageInfo(
        language = entity.language,
        role = when (entity.role) {
            MemberLanguage.Role.NATIVE -> LanguageReader.Role.NATIVE
            MemberLanguage.Role.LEARNING -> LanguageReader.Role.LEARNING
        },
        level = when (entity.level) {
            MemberLanguage.Level.BEGINNER -> LanguageReader.Level.BEGINNER
            MemberLanguage.Level.INTERMEDIATE -> LanguageReader.Level.INTERMEDIATE
            MemberLanguage.Level.ADVANCED -> LanguageReader.Level.ADVANCED
            null -> null
        },
    )
}

package com.langlez.interest.application

import com.langlez.interest.domain.Interest
import com.langlez.interest.domain.InterestRepository
import com.langlez.interest.domain.MemberInterest
import com.langlez.interest.domain.MemberInterestRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.Locale

data class InterestView(val id: Long, val name: String)

@Service
class InterestService(
    private val interestRepo: InterestRepository,
    private val memberInterestRepo: MemberInterestRepository,
) {

    fun search(locale: Locale, term: String): List<InterestView> {
        val field = LocaleField.of(locale)
        return interestRepo.searchByColumn(field, term, SEARCH_LIMIT)
            .mapNotNull { interest -> interest.get(field)?.let { InterestView(interest.id, it) } }
    }

    fun setMemberInterests(memberId: Long, locale: Locale, names: List<String>) {
        val field = LocaleField.of(locale)
        val resolvedIds = names.distinct().map { resolveOrCreate(field, it).id }.toSet()

        val current = memberInterestRepo.findByMemberId(memberId)
        val currentIds = current.map { it.interestId }.toSet()

        val toAdd = resolvedIds - currentIds
        val toRemove = current.filter { it.interestId !in resolvedIds }

        if (toAdd.isNotEmpty()) {
            memberInterestRepo.saveAll(toAdd.map { MemberInterest(memberId, it) })
        }
        if (toRemove.isNotEmpty()) {
            memberInterestRepo.deleteAll(toRemove)
        }
    }

    fun getMemberInterests(memberId: Long, locale: Locale): List<InterestView> {
        val field = LocaleField.of(locale)
        return memberInterestRepo.findByMemberId(memberId).mapNotNull { mi ->
            val interest = interestRepo.findById(mi.interestId) ?: return@mapNotNull null
            val name = interest.get(field) ?: interest.get("en") ?: return@mapNotNull null
            InterestView(interest.id, name)
        }
    }

    fun getMemberInterestIds(memberId: Long): Set<Long> =
        memberInterestRepo.findByMemberId(memberId).map { it.interestId }.toSet()

    fun merge(fromId: Long, toId: Long) {
        require(fromId != toId) { "fromId and toId must differ" }
        val from = interestRepo.findById(fromId) ?: throw IllegalArgumentException("interest not found: $fromId")
        val to = interestRepo.findById(toId) ?: throw IllegalArgumentException("interest not found: $toId")

        Interest.LOCALE_FIELDS.forEach { field ->
            if (to.get(field) == null && from.get(field) != null) {
                to.set(field, from.get(field))
            }
        }
        interestRepo.save(to)

        val fromMembers = memberInterestRepo.findByInterestId(fromId)
        val toMemberIds = memberInterestRepo.findByInterestId(toId).map { it.memberId }.toSet()
        val toRepoint = fromMembers.filter { it.memberId !in toMemberIds }
        memberInterestRepo.saveAll(toRepoint.map { MemberInterest(it.memberId, toId) })
        if (fromMembers.isNotEmpty()) {
            memberInterestRepo.deleteAll(fromMembers)
        }

        interestRepo.delete(from)
    }

    private fun resolveOrCreate(field: String, name: String): Interest {
        interestRepo.findByColumn(field, name)?.let { return it }
        val created = Interest()
        created.set(field, name)
        return try {
            interestRepo.save(created)
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청으로 이미 같은 이름이 생성된 경우 — 재조회해서 사용
            interestRepo.findByColumn(field, name) ?: throw e
        }
    }

    companion object {
        private const val SEARCH_LIMIT = 10
    }
}

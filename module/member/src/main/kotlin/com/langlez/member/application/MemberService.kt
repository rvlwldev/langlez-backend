package com.langlez.member.application

import com.langlez.core.event.member.MemberNicknameChangedEvent
import com.langlez.core.event.member.MemberUsernameChangedEvent
import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val repo: MemberRepository,
    private val creator: MemberCreator,
    private val tracker: MemberOnlineTracker,
    private val publisher: ApplicationEventPublisher
) {

    @Retryable(maxAttempts = 3, backoff = Backoff(100), retryFor = [DataIntegrityViolationException::class])
    fun createMember(
        providerType: Member.Provider,
        providerId: String,
        email: String,
        providerUsername: String,
        nickname: String
    ): Member = creator.create(providerType, providerId, email, providerUsername, nickname)

    @Transactional(readOnly = true)
    fun findById(id: Long): Member? = repo.find(id)

    @Transactional(readOnly = true)
    fun findByProvider(type: Member.Provider, providerId: String): Member? =
        repo.find(type, providerId)

    @Transactional(readOnly = true)
    fun findByEmail(email: String): Member? = repo.findByEmail(email)

    @Transactional
    fun updateUsername(id: Long, newUsername: String): Member {
        if (repo.find(newUsername) != null)
            throw LanglezException(HttpStatus.CONFLICT, "member.username.duplicated")

        val member = findOrThrow(id).apply { tracker.toOnline(username) }

        try {
            member.changeUsername(newUsername)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        return runCatching { repo.save(member) }
            .getOrElse { e -> throw LanglezException(HttpStatus.CONFLICT, "member.username.duplicated", e) }
            .also { publisher.publishEvent(MemberUsernameChangedEvent(id, member.username)) }
    }

    @Transactional
    fun updateNickname(id: Long, newNickname: String): Member {
        val member = findOrThrow(id)

        try {
            member.changeNickname(newNickname)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        return member.also(repo::save)
            .also { publisher.publishEvent(MemberNicknameChangedEvent(id, newNickname)) }
    }

    @Transactional(readOnly = true)
    fun isOnline(username: String): Boolean = findOrThrow(username)
        .let { member -> tracker.checkStatus(member.username) == true }

    @Transactional
    fun verify(id: Long) = findOrThrow(id).apply { verify() }
        .also(repo::save)

    @Transactional
    fun updateMarketingPolicy(id: Long, agree: Boolean) {
        findOrThrow(id).apply { agreedMarketingReceive = agree }
            .also(repo::save)
    }

    @Transactional
    fun updateLastAccess(id: Long) {
        (repo.find(id) ?: return)
            .apply { updateAccessedAt() }
            .apply { runCatching { tracker.toOnline(username) } }
            .also(repo::save)
    }

    fun presignProfileUrl(id: Long) {
        TODO()
    }

    fun updateProfileUrl(id: Long, url: String) {
        TODO()
    }

    @Transactional
    fun updateFcmToken(id: Long, token: String) {
        findOrThrow(id).apply { fcm = token }
            .also(repo::save)
    }

    fun suspendMember(id: Long, reason: String? = null) {
        TODO()
    }

    fun withdrawMember(id: Long) {
        TODO()
    }

    private fun findOrThrow(id: Long) = repo.find(id)
        ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")

    private fun findOrThrow(username: String) = repo.find(username)
        ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
}

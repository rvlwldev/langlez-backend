package com.langlez.member.application

import com.langlez.core.event.member.MemberNicknameChangedEvent
import com.langlez.core.event.member.MemberUsernameChangedEvent
import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
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
        providerType: MemberProvider,
        providerId: String,
        email: String,
        providerUsername: String,
        nickname: String
    ): Member = creator.create(providerType, providerId, email, providerUsername, nickname)

    @Transactional(readOnly = true)
    fun findById(id: Long): Member? = repo.findById(id)

    @Transactional(readOnly = true)
    fun findByEmail(email: String): Member? = repo.findByEmail(email)

    @Transactional(readOnly = true)
    fun findByProvider(type: MemberProvider, providerId: String): Member? =
        repo.findByProvider(type, providerId)

    @Transactional(readOnly = true)
    fun isOnline(username: String): Boolean =
        repo.findByUsername(username)
            ?.let { member -> tracker.checkStatus(member.username) == true }
            ?: throw LanglezException(404, "member.not-found")

    @Transactional
    fun updateVerifiedAt(id: Long) {
        (repo.findById(id) ?: throw LanglezException(404, "member.not-found"))
            .apply { verify() }
            .also(repo::save)
    }

    @Transactional
    fun updateMarketingAgree(id: Long, agree: Boolean) {
        (repo.findById(id) ?: throw LanglezException(404, "member.not-found"))
            .apply { agreedMarketingReceive = agree }
            .also(repo::save)
    }

    @Transactional
    fun updateLastAccess(id: Long) {
        (repo.findById(id) ?: return)
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
    fun updateUsername(id: Long, newUsername: String): Member {
        if (repo.findByUsername(newUsername) != null) throw LanglezException(409, "member.username.duplicated")

        val member = (repo.findById(id) ?: throw LanglezException(404, "member.not-found"))
            .apply { tracker.toOnline(username) }
            .apply { changeUsername(newUsername) }

        return runCatching { repo.save(member) }
            .getOrElse { e -> throw LanglezException(409, "member.username.duplicated", e) }
            .also { publisher.publishEvent(MemberUsernameChangedEvent(id, member.username)) }
    }

    @Transactional
    fun updateNickname(id: Long, newNickname: String): Member =
        (repo.findById(id) ?: throw LanglezException(404, "member.not-found"))
            .apply { changeNickname(newNickname) }
            .also(repo::save)
            .also { publisher.publishEvent(MemberNicknameChangedEvent(id, newNickname)) }

    @Transactional
    fun updateFcmToken(id: Long, token: String) {
        (repo.findById(id) ?: throw LanglezException(404, "member.not-found"))
            .apply { fcm = token }
            .also(repo::save)
    }

    fun suspendMember(id: Long, reason: String? = null) {
        TODO()
    }

    fun withdrawMember(id: Long) {
        TODO()
    }
}

package com.langlez.member.application

import com.langlez.core.event.member.MemberNicknameChangedEvent
import com.langlez.core.event.member.MemberUsernameChangedEvent
import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
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
    private val tracker: MemberOnlineTracker,
    private val publisher: ApplicationEventPublisher,
    private val memberCreator: MemberCreator,
) {

    @Retryable(maxAttempts = 3, backoff = Backoff(100), retryFor = [DataIntegrityViolationException::class])
    fun createMember(
        providerType: MemberProvider,
        providerId: String,
        email: String,
        providerUsername: String,
        nickname: String
    ): Member = memberCreator.create(providerType, providerId, email, providerUsername, nickname)

    @Transactional
    fun updateUsername(id: Long, newUsername: String): Member {
        val member = repo.findById(id)
            ?: throw LanglezException(404, "member.not-found")

        if (!Member.isValidUsername(newUsername))
            throw LanglezException(400, "member.username.invalid")

        if (!member.canChangeUsername())
            throw LanglezException(400, "member.username.cooldown")

        if (newUsername != member.username && repo.findByUsername(newUsername) != null)
            throw LanglezException(409, "member.username.duplicated")

        val oldUsername = member.username
        member.changeUsername(newUsername)
        val saved = try {
            repo.save(member)
        } catch (e: DataIntegrityViolationException) {
            throw LanglezException(409, "member.username.duplicated", e)
        }
        publisher.publishEvent(MemberUsernameChangedEvent(saved.id, oldUsername, newUsername))
        return saved
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): Member? = repo.findById(id)

    @Transactional(readOnly = true)
    fun findByEmail(email: String): Member? = repo.findByEmail(email)

    @Transactional(readOnly = true)
    fun findByProvider(type: MemberProvider, providerId: String): Member? =
        repo.findByProvider(type, providerId)

    @Transactional
    fun updateLastAccess(id: Long) {
        val member = repo.findById(id) ?: return
        member.updateAccessedAt()
        repo.save(member)
        runCatching { tracker.toOnline(member.username) }
    }

    @Transactional
    fun updateNickname(id: Long, newNickname: String): Member {
        val member = repo.findById(id)
            ?: throw LanglezException(404, "member.not-found")

        if (!member.canChangeNickname())
            throw LanglezException(400, "member.nickname.cooldown")

        member.changeNickname(newNickname)
        val saved = repo.save(member)
        publisher.publishEvent(MemberNicknameChangedEvent(saved.id, newNickname))
        return saved
    }

    @Transactional
    fun updateFcmToken(memberId: Long, token: String) {
        val member = repo.findById(memberId)
            ?: throw LanglezException(404, "member.not-found")
        member.fcm = token
        repo.save(member)
    }

    @Transactional(readOnly = true)
    fun isOnline(username: String): Boolean {
        val member = repo.findByUsername(username)
            ?: throw LanglezException(404, "member.not-found")
        return tracker.checkStatus(member.username) == true
    }
}

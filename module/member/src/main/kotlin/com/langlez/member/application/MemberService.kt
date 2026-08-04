package com.langlez.member.application

import com.langlez.core.Storage
import com.langlez.core.event.member.MemberHandleChangedEvent
import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class MemberService(
    private val repo: MemberRepository,
    private val creator: MemberCreator,
    private val tracker: MemberOnlineTracker,
    private val storage: Storage,
    private val publisher: ApplicationEventPublisher,
    private val suspendRepo: MemberSuspendHistoryRepository,
) {

    @Retryable(maxAttempts = 3, backoff = Backoff(100), retryFor = [DataIntegrityViolationException::class])
    fun createMember(
        providerType: Member.Provider,
        providerId: String,
        email: String,
        providerUsername: String,
    ): Member = creator.create(providerType, providerId, email, providerUsername)

    @Transactional(readOnly = true)
    fun findById(id: Long): Member? = repo.find(id)

    @Transactional(readOnly = true)
    fun findByProvider(type: Member.Provider, providerId: String): Member? =
        repo.find(type, providerId)

    @Transactional(readOnly = true)
    fun findByEmail(email: String): Member? = repo.findByEmail(email)

    @Transactional
    fun updateHandle(id: Long, newHandle: String): Member {
        if (repo.find(newHandle) != null)
            throw LanglezException(HttpStatus.CONFLICT, "member.handle.duplicated")

        val member = findOrThrow(id)
        val oldHandle = member.handle

        try {
            member.changeHandle(newHandle)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        return runCatching { repo.save(member) }
            .getOrElse { e -> throw LanglezException(HttpStatus.CONFLICT, "member.handle.duplicated", e) }
            // 온라인 표시는 핸들로 keying 한다. 바꾼 뒤에 옮겨 달지 않으면 옛 핸들이 온라인으로 남는다.
            .also { tracker.toOffline(oldHandle) }
            .also { tracker.toOnline(member.handle) }
            .also { publisher.publishEvent(MemberHandleChangedEvent(id, member.handle)) }
    }

    @Transactional(readOnly = true)
    fun isOnline(handle: String): Boolean = findOrThrow(handle)
        .let { member -> tracker.checkStatus(member.handle) == true }

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
            .apply { runCatching { tracker.toOnline(handle) } }
            .also(repo::save)
    }

    fun presignProfileUrl(id: Long, filename: String) =
        storage.presign(id, "member", Storage.Type.IMAGE, filename)

    // storage.attach()가 S3 HeadObject/파일존재확인 등 블로킹 I/O라 트랜잭션 밖에서 먼저 끝낸다.
    // repo.save() 자체가 자기 트랜잭션을 가지니 여기서 따로 @Transactional을 걸 필요 없다.
    fun updateProfileUrl(id: Long, key: String): Member {
        val member = findOrThrow(id)
        member.imageUrl = storage.attach(key, id)
        return member.also(repo::save)
    }

    @Transactional
    fun updateFcmToken(id: Long, token: String) {
        findOrThrow(id).apply { fcm = token }
            .also(repo::save)
    }

    @Transactional
    fun suspendMember(id: Long, reason: String? = null, days: Long? = null) {
        val member = findOrThrow(id)

        try {
            member.suspend()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        repo.save(member)
        suspendRepo.save(MemberSuspendHistory(member, reason, days?.let { Duration.ofDays(it) }))
    }

    @Transactional
    fun withdrawMember(id: Long) {
        findOrThrow(id).apply { withdraw() }
            .also(repo::save)
    }

    private fun findOrThrow(id: Long) = repo.find(id)
        ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")

    private fun findOrThrow(handle: String) = repo.find(handle)
        ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
}

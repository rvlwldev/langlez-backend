package com.langlez.member.application

import com.langlez.core.OnlineTracker
import com.langlez.core.Storage
import com.langlez.core.event.member.MemberHandleChangedEvent
import com.langlez.core.event.member.MemberWithdrawnEvent
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
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDate

@Service
class MemberService(
    private val repo: MemberRepository,
    private val creator: MemberCreator,
    private val tracker: OnlineTracker,
    private val storage: Storage,
    private val publisher: ApplicationEventPublisher,
    private val suspendRepo: MemberSuspendHistoryRepository,
    private val tx: TransactionTemplate,
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

        try {
            member.changeHandle(newHandle)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        return runCatching { repo.save(member) }
            .getOrElse { e -> throw LanglezException(HttpStatus.CONFLICT, "member.handle.duplicated", e) }
            // 온라인 표시는 id로 keying하니 handle이 바뀌어도 옮겨 달 필요가 없다.
            .also { publisher.publishEvent(MemberHandleChangedEvent(id, member.handle)) }
    }

    @Transactional(readOnly = true)
    fun isOnline(handle: String): Boolean = findOrThrow(handle)
        .let { member -> tracker.checkOnline(member.id)[member.id] == true }

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
            .apply { runCatching { tracker.toOnline(this.id) } }
            .also(repo::save)
    }

    fun presignProfileUrl(id: Long, filename: String) =
        storage.presign(id, "member", Storage.Type.IMAGE, filename)

    /**
     * 프로필 이미지 확정.
     *
     * `storage.attach` 는 S3 확인 등 블로킹 I/O 라 트랜잭션 밖에서 먼저 끝낸다.
     * 그 뒤 읽기+쓰기만 하나의 트랜잭션으로 묶는다. 예전처럼 조회와 저장이 서로 다른
     * 트랜잭션이면 그 사이 다른 수정이 끼어들어 @Version 경합(lost update)이 난다.
     * 반환은 반드시 save 결과여야 한다. merge 이전 detached 인스턴스를 돌려주면
     * 응답에 실제 저장된 상태가 아닌 값이 실린다.
     */
    fun updateProfileUrl(id: Long, key: String): Member {
        val imageUrl = storage.attach(key, id)

        return tx.execute {
            findOrThrow(id)
                .apply { this.imageUrl = imageUrl }
                .let(repo::save)
        }!!
    }

    /**
     * 성별·생년월일·국가·닉네임 부분 수정. null 인 항목은 그대로 둔다.
     *
     * 값을 지우는 경로는 없다. null 이 "지움"이면 안 보낸 필드까지 같이 날아간다.
     */
    @Transactional
    fun updatePersonalInfo(
        id: Long,
        gender: Member.Gender?,
        birthDay: LocalDate?,
        country: String?,
        nickname: String?,
    ): Member {
        val member = findOrThrow(id).apply {
            gender?.let { this.gender = it }
            birthDay?.let { this.birthDay = it }
            country?.let { this.country = it }
        }

        try {
            nickname?.let { member.changeNickname(it) }
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        return repo.save(member)
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

    /** 어드민 정지 해제. 정지 이력도 해제 처리한다. */
    @Transactional
    fun unsuspendMember(id: Long) {
        val member = findOrThrow(id)

        try {
            member.unsuspend()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        repo.save(member)
    }

    @Transactional
    fun withdrawMember(id: Long) {
        findOrThrow(id).apply { withdraw() }
            .also(repo::save)
            .also { publisher.publishEvent(MemberWithdrawnEvent(id)) }
    }

    private fun findOrThrow(id: Long) = repo.find(id)
        ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")

    private fun findOrThrow(handle: String) = repo.find(handle)
        ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
}

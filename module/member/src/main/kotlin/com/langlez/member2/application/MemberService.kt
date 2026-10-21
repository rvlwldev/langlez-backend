package com.langlez.member2.application

import com.langlez.exception.LanglezException
import com.langlez.member2.infrastructure.MemberRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.random.Random

@Service
class MemberService(val repo: MemberRepository, val tx: TransactionTemplate) {

    fun authenticate(provider: String, providerId: String, email: String?, displayName: String) {
        tx.execute { /* 멤버조회 후 정지/탈퇴 확인 */ }

        // 존재하지 않는 멤버라면 생성
        createMember()
    }

    fun createMember() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw LanglezException(HttpStatus.INTERNAL_SERVER_ERROR)

        val tries = 3
        repeat(tries) { attempt ->
            try {
                return tx.execute { /* 멤버 생성 */ }
                    ?: error("트랜잭션 콜백은 null 을 반환하지 않는다")
            } catch (e: DataIntegrityViolationException) {
                if (attempt == tries - 1) throw e
                Thread.sleep(Random.nextLong(50, 150))
            }
        }
    }

    @Transactional
    fun verify(id: Long) {
    }

    @Transactional(readOnly = true)
    fun findMember(id: Long) {
    }

    @Transactional(readOnly = true)
    fun findMember(handle: String) {
    }

    @Transactional(readOnly = true)
    fun searchMembers(query: String) {
    }

    @Transactional
    fun withdraw(id: Long) {
    }

    fun cancelWithdraw() {}

    fun report() {}

    fun suspend() {}

    fun unsuspend() {}

    fun toOnline(id: Long) {}
    fun isOnline(id: Long) {}

}
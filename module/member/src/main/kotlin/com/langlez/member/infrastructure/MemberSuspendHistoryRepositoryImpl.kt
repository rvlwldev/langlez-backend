package com.langlez.member.infrastructure

import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import com.langlez.member.infrastructure.jpa.MemberSuspendHistoryJpaRepository
import org.springframework.stereotype.Repository

@Repository
class MemberSuspendHistoryRepositoryImpl(
    private val jpa: MemberSuspendHistoryJpaRepository,
) : MemberSuspendHistoryRepository {

    override fun save(history: MemberSuspendHistory): MemberSuspendHistory = jpa.save(history)
}

package com.langlez.member.domain

interface MemberSuspendHistoryRepository {
    fun save(history: MemberSuspendHistory): MemberSuspendHistory
}

package com.langlez.chat.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
// 발행 폴링 인덱스는 `where status = 'PENDING'` 부분 인덱스라 JPA 의 @Index 로 표현할 방법이 없다.
// 실제 DDL 은 V17 이 만든다 (IDX_CHAT_EVENT_OUTBOX_PENDING (created_at) where status = 'PENDING').
// validate 는 인덱스를 보지 않으니 여기 선언이 없다고 인덱스가 없는 게 아니다.
@Table(name = "chat_event_outbox")
class ChatOutBox(
    domain: String,
    topic: String,
    payload: String,
    key: String? = null
) : OutBox(domain, topic, payload, key)

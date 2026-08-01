package com.langlez.chat.infrastructure.outbox

import com.langlez.rdb.outbox.OutBoxRepository

interface ChatOutBoxRepository : OutBoxRepository<ChatOutBox, ChatOutBoxHistory>

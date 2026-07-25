package com.langlez.chat.infrastructure.outbox

import com.langlez.mysql.outbox.OutBoxRepository

interface ChatOutBoxRepository : OutBoxRepository<ChatOutBox, ChatOutBoxHistory>

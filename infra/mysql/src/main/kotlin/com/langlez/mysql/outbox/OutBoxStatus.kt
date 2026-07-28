package com.langlez.mysql.outbox

enum class OutBoxStatus { READY, PROCESSING, COMPLETE, FAILED }
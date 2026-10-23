package com.langlez.consumer

import com.langlez.redis.lock.DistributedLock
import com.langlez.redis.lock.LockKey
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Service

@SpringBootApplication
class ConsumerApp

@Service
class DemoService {

    @DistributedLock(prefix = "lock:room:", keys = ["#roomId"], throwOnFailure = true)
    fun work(roomId: Long, sleepMs: Long): String {
        Thread.sleep(sleepMs)
        return "ok-$roomId"
    }

    @DistributedLock(prefix = "lock:skip:", keys = ["#id"])
    fun skippable(id: Long, sleepMs: Long): String? {
        Thread.sleep(sleepMs)
        return "ran-$id"
    }

    @DistributedLock(prefix = "lock:tag:")
    fun tagged(@LockKey tenant: String, @LockKey id: Long, sleepMs: Long): String {
        Thread.sleep(sleepMs)
        return "$tenant-$id"
    }

    @DistributedLock(prefix = "lock:broken:", keys = ["#missing.name"])
    fun broken(id: Long): String = "never-$id"

    @DistributedLock(prefix = "lock:retry:", keys = ["#id"], retries = 10, waitMs = 200, throwOnFailure = true)
    fun retrying(id: Long, sleepMs: Long): String {
        Thread.sleep(sleepMs)
        return "retried-$id"
    }
}

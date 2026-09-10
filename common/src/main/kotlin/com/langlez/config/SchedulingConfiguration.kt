package com.langlez.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * 애플리케이션 스케줄러 설정.
 *
 * Spring WebSocket(@EnableWebSocketMessageBroker)이 활성화되면 STOMP 하트비트용
 * `messageBrokerTaskScheduler` 가 생성된다. 별도의 TaskScheduler 빈이 없으면
 * Spring 은 @Scheduled 전부를 하트비트용 풀(MessageBroker-*)에 위임한다 (README §5.2-7).
 * 아웃박스 폴러, 캐시 헬스체크, 접속 동기화 등의 I/O 지연이 WebSocket 하트비트를 블로킹하지 않도록
 * 애플리케이션 전용 TaskScheduler 를 등록하고 @Primary 및 SchedulingConfigurer 로 격리한다.
 */
@Configuration
class SchedulingConfiguration : SchedulingConfigurer {

    private val scheduler: ThreadPoolTaskScheduler by lazy {
        ThreadPoolTaskScheduler().apply {
            poolSize = POOL_SIZE
            setThreadNamePrefix("app-scheduled-")
            setRemoveOnCancelPolicy(true)
            initialize()
        }
    }

    @Bean(name = ["taskScheduler"])
    @Primary
    fun taskScheduler(): ThreadPoolTaskScheduler = scheduler

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setTaskScheduler(scheduler)
    }

    companion object {
        private const val POOL_SIZE = 10
    }
}

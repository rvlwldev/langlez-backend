package com.langlez.observability

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class StartupQueryTester(
    private val jdbcTemplate: JdbcTemplate,
    private val mongoTemplate: org.springframework.data.mongodb.core.MongoTemplate,
    private val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate,
) {
    private val logger = LoggerFactory.getLogger(StartupQueryTester::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun runTestQueries() {
        logger.info("==========================================================================================")
        logger.info("  Executing Startup Test Queries for Full Stack Monitoring Verification...")

        try {
            // 1. MySQL (JDBC/P6Spy)
            logger.info("  [MySQL] Executing 'SELECT 1'...")
            val mysqlResult = jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            logger.info("  [MySQL] Result: {}", mysqlResult)

            // 2. MongoDB
            logger.info("  [MongoDB] Executing 'ping' (via collection count)...")
            // Create a dummy document to ensure collection exists or just count
            if (!mongoTemplate.collectionExists("monitoring_test")) {
                mongoTemplate.createCollection("monitoring_test")
            }
            val mongoCount =
                mongoTemplate.count(
                    org.springframework.data.mongodb.core.query
                        .Query(),
                    "monitoring_test",
                )
            logger.info("  [MongoDB] Collection 'monitoring_test' count: {}", mongoCount)

            // 3. Redis
            logger.info("  [Redis] Executing 'SET/GET'...")
            redisTemplate.opsForValue().set("monitoring:test", "hello")
            val redisValue = redisTemplate.opsForValue().get("monitoring:test")
            logger.info("  [Redis] Result for key 'monitoring:test': {}", redisValue)

            logger.info("  Full Stack Test Queries Completed.")
        } catch (e: Exception) {
            logger.error("  Failed to execute test queries: {}", e.message, e)
        }
        logger.info("==========================================================================================")
    }
}

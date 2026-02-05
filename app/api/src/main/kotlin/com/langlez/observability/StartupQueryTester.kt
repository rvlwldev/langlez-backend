package com.langlez.observability

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Component

@Component
class StartupQueryTester(
    private val jdbc: JdbcTemplate,
    private val mongo: MongoTemplate,
    private val redis: StringRedisTemplate,
) {
    private val logger = LoggerFactory.getLogger(StartupQueryTester::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun runTestQueries() {
        logger.info("==========================================================================================")
        logger.info("  Executing Startup Test Queries for Full Stack Monitoring Verification...")

        try {
            // 1. MySQL (JDBC/P6Spy)
            logger.info("  [MySQL] Executing 'SELECT 1'...")
            val mysqlResult = jdbc.queryForObject<Int>("SELECT 1")
            logger.info("  [MySQL] Result: {}", mysqlResult)

            // 2. MongoDB
            // Create a dummy document to ensure collection exists or just count
            logger.info("  [MongoDB] Executing 'ping' (via collection count)...")
            if (!mongo.collectionExists("monitoring_test"))
                mongo.createCollection("monitoring_test")
            val mongoCount =mongo.count(Query(), "monitoring_test")
            logger.info("  [MongoDB] Collection 'monitoring_test' count: {}", mongoCount)

            // 3. Redis
            logger.info("  [Redis] Executing 'SET/GET'...")
            redis.opsForValue().set("monitoring:test", "hello")
            val redisValue = redis.opsForValue().get("monitoring:test")
            logger.info("  [Redis] Result for key 'monitoring:test': {}", redisValue)

            logger.info("  Full Stack Test Queries Completed.")
        } catch (e: Exception) {
            logger.error("  Failed to execute test queries: {}", e.message, e)
        }

        logger.info("==========================================================================================")
    }
}

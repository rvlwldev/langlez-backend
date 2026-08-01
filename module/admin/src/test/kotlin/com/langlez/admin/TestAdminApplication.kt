package com.langlez.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration

@SpringBootApplication(
    scanBasePackages = ["com.langlez.admin"],
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        MongoDataAutoConfiguration::class,
        MongoAutoConfiguration::class,
        RedisAutoConfiguration::class
    ],
    excludeName = [
        "com.langlez.mongo.config.MongoConfiguration",
        "com.langlez.rdb.RdbConfiguration"
    ]
)
class TestAdminApplication

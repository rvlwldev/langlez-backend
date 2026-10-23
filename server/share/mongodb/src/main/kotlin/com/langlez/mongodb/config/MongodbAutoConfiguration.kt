package com.langlez.mongodb.config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@AutoConfiguration(beforeName = ["org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration"])
@ConditionalOnClass(MongoTemplate::class)
@EnableMongoRepositories(basePackages = ["com.langlez.**.mongo"])
class MongodbAutoConfiguration

package com.langlez.consumer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan

@SpringBootApplication
@EntityScan("com.langlez.demo.jpa")
class ConsumerApp

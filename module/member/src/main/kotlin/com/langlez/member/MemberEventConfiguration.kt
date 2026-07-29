package com.langlez.member

import com.langlez.core.MessageTopic
import com.langlez.core.MessageSemantic
import com.langlez.core.MessageSemantic.AMO
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MemberEventConfiguration {

    @Bean
    fun memberOnline(): MessageTopic = MessageTopic("member-username-changed", 10, AMO)

}
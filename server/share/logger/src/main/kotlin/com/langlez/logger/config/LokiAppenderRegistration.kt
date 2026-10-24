package com.langlez.logger.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.github.loki4j.logback.JsonLayout
import com.github.loki4j.logback.Loki4jAppender
import com.github.loki4j.logback.PipelineConfigAppenderBase.BasicAuth
import com.github.loki4j.logback.PipelineConfigAppenderBase.BatchCfg
import com.github.loki4j.logback.PipelineConfigAppenderBase.HttpCfg
import com.github.loki4j.logback.json.MdcJsonProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean

internal class LokiAppenderRegistration(
    private val properties: LokiProperties,
    private val service: String,
) : InitializingBean, DisposableBean {

    private val root: Logger = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(Logger.ROOT_LOGGER_NAME)
    private val appender = Loki4jAppender()

    override fun afterPropertiesSet() {
        appender.context = root.loggerContext
        appender.name = "LOKI"
        appender.setLabels("service=$service\nlevel=%level")
        appender.setMessage(JsonLayout().apply { setMdc(MdcJsonProvider().apply { setNoPrefix(true) }) })
        appender.setHttp(http())
        appender.setBatch(BatchCfg().apply { setTimeoutMs(properties.batchTimeout.toMillis()) })
        appender.start()
        root.addAppender(appender)
    }

    override fun destroy() {
        root.detachAppender(appender)
        appender.stop()
    }

    private fun http() = HttpCfg().apply {
        setUrl(properties.pushUrl)
        if (properties.username != null) {
            setAuth(BasicAuth().apply {
                setUsername(properties.username)
                setPassword(properties.password.orEmpty())
            })
        }
    }
}

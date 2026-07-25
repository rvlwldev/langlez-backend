package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.Hashtag
import org.springframework.data.jpa.repository.JpaRepository

interface HashtagJpaRepository : JpaRepository<Hashtag, Long> {
    fun findByName(name: String): Hashtag?
    fun findByNameIn(names: Collection<String>): List<Hashtag>
}

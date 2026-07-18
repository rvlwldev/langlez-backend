package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.PostHashtag
import org.springframework.data.jpa.repository.JpaRepository

interface PostHashtagJpaRepository : JpaRepository<PostHashtag, Long>

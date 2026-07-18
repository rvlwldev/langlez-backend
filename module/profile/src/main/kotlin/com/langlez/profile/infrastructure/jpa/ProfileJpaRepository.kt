package com.langlez.profile.infrastructure.jpa

import com.langlez.profile.domain.Profile
import org.springframework.data.jpa.repository.JpaRepository

interface ProfileJpaRepository : JpaRepository<Profile, Long>

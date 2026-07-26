package com.langlez.interest.infrastructure.jpa

import com.langlez.interest.domain.Interest
import org.springframework.data.jpa.repository.JpaRepository

interface InterestJpaRepository : JpaRepository<Interest, Long>

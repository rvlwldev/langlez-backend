package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY
import java.time.LocalDate

@Entity
@Table(
    name = "hashtag_daily_stat",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_HASHTAG_DAILY_STAT", columnNames = ["hashtag", "stat_date"])]
)
class HashtagDailyStat(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val hashtag: String,

    @Column(name = "stat_date", nullable = false)
    val statDate: LocalDate,

    @Column(name = "post_count", nullable = false)
    var postCount: Long,

    @Column(name = "search_count", nullable = false)
    var searchCount: Long
)

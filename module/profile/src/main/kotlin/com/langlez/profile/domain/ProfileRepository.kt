package com.langlez.profile.domain

interface ProfileRepository {

    fun saveImage(image: ProfileImage): ProfileImage
    fun findRepresentImage(id: Long): ProfileImage?
    fun countImages(id: Long): Long

    fun findProfile(id: Long): Profile?
    fun saveProfile(profile: Profile): Profile

    fun increaseVisitCount(visitorId: Long, username: String)
    fun getVisitCountDelta(username: String): Long
    fun flushVisitCounts(): Map<String, Long>

}

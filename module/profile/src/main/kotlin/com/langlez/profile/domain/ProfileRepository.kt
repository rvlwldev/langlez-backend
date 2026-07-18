package com.langlez.profile.domain

interface ProfileRepository {

    fun saveImage(image: ProfileImage): ProfileImage
    fun findRepresentImage(id: Long): ProfileImage?
    fun findImageByUrl(memberId: Long, url: String): ProfileImage?
    fun countImages(id: Long): Long

    fun findProfile(id: Long): Profile?
    fun findProfileByUsername(username: String): Profile?
    fun findAllProfiles(): List<Profile>
    fun saveProfile(profile: Profile): Profile

    fun increaseVisitCount(visitorId: Long, username: String)
    fun getVisitCountDelta(username: String): Long
    fun beginVisitCountFlush(): Map<String, Long>
    fun commitVisitCountFlush(usernames: Collection<String>)
    fun incrementVisitCountInDb(username: String, delta: Long)

}

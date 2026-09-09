package com.langlez.profile.domain

interface ProfileRepository {

    fun saveImage(image: ProfileImage): ProfileImage
    fun findRepresentImage(id: Long): ProfileImage?
    fun findImageByUrl(memberId: Long, url: String): ProfileImage?
    fun countImages(id: Long): Long

    fun findProfile(id: Long): Profile?
    fun findProfiles(ids: List<Long>): List<Profile>
    fun findAllProfiles(): List<Profile>
    fun saveProfile(profile: Profile): Profile

    fun increaseVisitCount(visitorId: Long, memberId: Long)
    fun getVisitCountDelta(memberId: Long): Long
    fun beginVisitCountFlush(): Map<Long, Long>
    fun commitVisitCountFlush(memberIds: Collection<Long>)
    fun incrementVisitCountInDb(memberId: Long, delta: Long)

}

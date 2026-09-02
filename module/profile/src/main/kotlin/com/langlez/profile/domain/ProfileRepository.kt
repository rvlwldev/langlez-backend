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

    fun increaseVisitCount(visitorId: Long, username: String)
    fun getVisitCountDelta(username: String): Long
    fun beginVisitCountFlush(): Map<String, Long>
    fun commitVisitCountFlush(usernames: Collection<String>)
    /** handle → 회원 id 변환은 application 이 미리 해서 넘긴다. 저장소가 member 포트를 부르면 안 된다. */
    fun incrementVisitCountInDb(memberId: Long, delta: Long)

}

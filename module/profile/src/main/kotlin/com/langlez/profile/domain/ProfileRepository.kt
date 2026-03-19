package com.langlez.profile.domain

interface ProfileRepository {

    fun saveImage(image: ProfileImage): ProfileImage
    fun findRepresentImage(id: Long): ProfileImage?
    fun countImages(id: Long): Long

}
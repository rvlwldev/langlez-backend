package com.langlez.profile.api

import com.langlez.profile.application.ProfileResponse
import com.langlez.profile.application.ProfileService
import com.langlez.security.web.MemberID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(private val service: ProfileService) {

    @GetMapping("/@{username}")
    fun getProfile(@MemberID visitorId: Long, @PathVariable username: String): ProfileResponse {
        return service.getProfile(visitorId, username)
    }
}

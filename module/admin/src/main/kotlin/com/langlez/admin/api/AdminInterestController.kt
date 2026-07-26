package com.langlez.admin.api

import com.langlez.interest.application.InterestService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.Locale

@Controller
@RequestMapping("/admin/interests")
class AdminInterestController(private val interestService: InterestService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        model: Model,
    ): String {
        val locale = Locale.forLanguageTag("en")
        val items = if (!q.isNullOrBlank()) interestService.search(locale, q) else emptyList()
        model.addAttribute("query", q ?: "")
        model.addAttribute("items", items)
        return "admin/interests"
    }

    @PostMapping("/merge")
    fun merge(@RequestParam fromId: Long, @RequestParam toId: Long): String {
        interestService.merge(fromId, toId)
        return "redirect:/admin/interests"
    }
}

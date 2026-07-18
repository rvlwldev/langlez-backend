package com.langlez.admin.api

import com.langlez.admin.application.AdminService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Instant

@Controller
@RequestMapping("/admin")
class AdminController(
    private val adminService: AdminService
) {

    @GetMapping("/login")
    fun login(): String {
        return "admin/login"
    }

    @GetMapping("")
    fun dashboard(model: Model): String {
        val dashboard = adminService.getDashboard()
        model.addAttribute("dashboard", dashboard)
        return "admin/dashboard"
    }

    @GetMapping("/users")
    fun users(
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
        model: Model
    ): String {
        val users = adminService.getUsers(cursor, size)
        val nextCursor = if (users.size == size) users.last().id else null
        model.addAttribute("users", users)
        model.addAttribute("nextCursor", nextCursor)
        model.addAttribute("size", size)
        return "admin/users"
    }

    @GetMapping("/users/{username}/chats")
    fun userChats(
        @PathVariable username: String,
        model: Model
    ): String {
        val chats = adminService.getUserChats(username)
        model.addAttribute("username", username)
        model.addAttribute("chats", chats)
        return "admin/user-chats"
    }

    @GetMapping("/chats")
    fun chats(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
        model: Model
    ): String {
        val chats = adminService.getAllChats(cursor, size)
        val nextCursor = if (chats.size == size) chats.last().roomId else null
        model.addAttribute("chats", chats)
        model.addAttribute("nextCursor", nextCursor)
        model.addAttribute("size", size)
        return "admin/chats"
    }

    @GetMapping("/chats/{roomId}")
    fun chatRoom(
        @PathVariable roomId: String,
        @RequestParam(defaultValue = "50") size: Int,
        model: Model
    ): String {
        val messages = adminService.getChatRoomMessages(roomId, null, size)
        val lastMessageAt = if (messages.isNotEmpty()) {
            messages.last().createdAt.toString()
        } else {
            Instant.EPOCH.toString()
        }
        model.addAttribute("roomId", roomId)
        model.addAttribute("messages", messages)
        model.addAttribute("lastMessageAt", lastMessageAt)
        return "admin/chat-room"
    }

    @GetMapping("/chats/{roomId}/poll")
    fun pollMessages(
        @PathVariable roomId: String,
        @RequestParam after: String,
        model: Model
    ): String {
        val since = Instant.parse(after)
        val messages = adminService.getChatRoomMessagesSince(roomId, since)
        model.addAttribute("messages", messages)
        return "admin/chat-room-messages-fragment :: messages"
    }

    @GetMapping("/attachments")
    fun attachments(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "30") size: Int,
        model: Model
    ): String {
        val attachments = adminService.getAttachments(cursor, size)
        val nextCursor = if (attachments.size == size) attachments.last().id else null
        model.addAttribute("attachments", attachments)
        model.addAttribute("nextCursor", nextCursor)
        model.addAttribute("size", size)
        return "admin/attachments"
    }
}

package com.langlez.interest.api

import com.langlez.interest.application.InterestView

class InterestResponse {
    data class Item(val id: Long, val name: String)
    data class List(val items: kotlin.collections.List<Item>)

    companion object {
        fun of(views: kotlin.collections.List<InterestView>) = List(views.map { Item(it.id, it.name) })
    }
}

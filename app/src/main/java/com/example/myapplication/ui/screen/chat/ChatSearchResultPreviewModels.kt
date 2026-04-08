package com.example.myapplication.ui.screen.chat

import com.example.myapplication.data.repository.search.SearchResultItem

data class ChatSearchResultPreviewPayload(
    val title: String,
    val query: String,
    val answer: String,
    val items: List<SearchResultItem>,
)

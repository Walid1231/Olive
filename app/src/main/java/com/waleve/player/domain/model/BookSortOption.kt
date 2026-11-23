package com.waleve.player.domain.model

/** Sort options tailored for audiobooks (a book = an album of chapters). */
enum class BookSortOption {
    RECENT,    // when the book's newest chapter was added
    TITLE,     // book name A–Z
    AUTHOR,    // narrator / author (artist) A–Z
    DURATION,  // total length of all chapters
    CHAPTERS,  // number of chapters
    PROGRESS,  // how far you've listened
}

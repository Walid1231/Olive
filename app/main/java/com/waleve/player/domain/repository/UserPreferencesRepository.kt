package com.waleve.player.domain.repository

import com.waleve.player.domain.model.SortOption
import com.waleve.player.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val isNightMode: Flow<Boolean>
    val sortOption: Flow<SortOption>
    val sortOrder: Flow<SortOrder>

    suspend fun setNightMode(isNightMode: Boolean)
    suspend fun setSortOption(option: SortOption)
    suspend fun setSortOrder(order: SortOrder)
}

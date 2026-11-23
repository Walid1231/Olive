package com.waleve.player.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.waleve.player.domain.model.BookSortOption
import com.waleve.player.domain.model.SortOption
import com.waleve.player.domain.model.SortOrder
import com.waleve.player.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepositoryImpl(
    private val context: Context
) : UserPreferencesRepository {

    private object PreferencesKeys {
        val IS_NIGHT_MODE = booleanPreferencesKey("is_night_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val BOOK_SORT_OPTION = stringPreferencesKey("book_sort_option")
        val BOOK_SORT_ORDER = stringPreferencesKey("book_sort_order")
    }

    override val isNightMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_NIGHT_MODE] ?: false
    }

    override val sortOption: Flow<SortOption> = context.dataStore.data.map { preferences ->
        val optionString = preferences[PreferencesKeys.SORT_OPTION] ?: SortOption.DATE_ADDED.name
        try {
            SortOption.valueOf(optionString)
        } catch (e: Exception) {
            SortOption.DATE_ADDED
        }
    }

    override val sortOrder: Flow<SortOrder> = context.dataStore.data.map { preferences ->
        val orderString = preferences[PreferencesKeys.SORT_ORDER] ?: SortOrder.DESCENDING.name
        try {
            SortOrder.valueOf(orderString)
        } catch (e: Exception) {
            SortOrder.DESCENDING
        }
    }

    override val bookSortOption: Flow<BookSortOption> = context.dataStore.data.map { preferences ->
        val optionString = preferences[PreferencesKeys.BOOK_SORT_OPTION] ?: BookSortOption.RECENT.name
        try {
            BookSortOption.valueOf(optionString)
        } catch (e: Exception) {
            BookSortOption.RECENT
        }
    }

    override val bookSortOrder: Flow<SortOrder> = context.dataStore.data.map { preferences ->
        val orderString = preferences[PreferencesKeys.BOOK_SORT_ORDER] ?: SortOrder.DESCENDING.name
        try {
            SortOrder.valueOf(orderString)
        } catch (e: Exception) {
            SortOrder.DESCENDING
        }
    }

    override suspend fun setNightMode(isNightMode: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_NIGHT_MODE] = isNightMode
        }
    }

    override suspend fun setSortOption(option: SortOption) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_OPTION] = option.name
        }
    }

    override suspend fun setSortOrder(order: SortOrder) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_ORDER] = order.name
        }
    }

    override suspend fun setBookSortOption(option: BookSortOption) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BOOK_SORT_OPTION] = option.name
        }
    }

    override suspend fun setBookSortOrder(order: SortOrder) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BOOK_SORT_ORDER] = order.name
        }
    }
}

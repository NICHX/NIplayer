package com.xyoye.local_component.ui.activities.scrape

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.data_component.entity.ScrapeMediaEntity
import com.xyoye.data_component.entity.TmdbSearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchMatchViewModel : BaseViewModel() {

    private val tmdbRepository = TmdbRepository()

    private val _searchResultLiveData = MutableLiveData<MutableList<TmdbSearchItem>>()
    val searchResultLiveData: LiveData<MutableList<TmdbSearchItem>> = _searchResultLiveData

    fun searchMulti(query: String) {
        viewModelScope.launch(context = Dispatchers.IO) {
            val tmdbKey = getTmdbApiKey()
            if (tmdbKey.isEmpty()) return@launch

            try {
                val result = tmdbRepository.searchMulti(query, tmdbKey)
                _searchResultLiveData.postValue(result.results.toMutableList())
            } catch (e: Exception) {
                _searchResultLiveData.postValue(mutableListOf())
            }
        }
    }

    fun selectMatch(mediaId: Int, item: TmdbSearchItem, mediaType: String) {
        viewModelScope.launch(context = Dispatchers.IO) {
            val existing = DatabaseManager.instance.getScrapeMediaDao().getById(mediaId) ?: return@launch

            val updated = existing.copy(
                poster = item.poster_path,
                backdrop = item.backdrop_path,
                tmdbId = item.id,
                genreIds = JsonHelper.toJson(item.genre_ids) ?: "[]",
                voteAverage = item.vote_average,
                releaseTime = item.release_date ?: item.first_air_date,
                overview = item.overview
            )

            DatabaseManager.instance.getScrapeMediaDao().update(updated)
        }
    }

    private fun getTmdbApiKey(): String {
        return com.tencent.mmkv.MMKV.defaultMMKV()
            ?.decodeString("tmdb_api_key", "") ?: ""
    }
}

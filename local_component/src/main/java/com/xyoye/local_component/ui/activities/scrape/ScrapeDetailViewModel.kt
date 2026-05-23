package com.xyoye.local_component.ui.activities.scrape

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.data_component.entity.EpisodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScrapeDetailViewModel : BaseViewModel() {

    private val _episodes = MutableLiveData<List<EpisodeEntity>>()
    val episodes: LiveData<List<EpisodeEntity>> = _episodes

    private val _seasons = MutableLiveData<List<Int>>()
    val seasons: LiveData<List<Int>> = _seasons

    private val _currentSeason = MutableLiveData(1)
    val currentSeason: LiveData<Int> = _currentSeason

    fun loadEpisodes(mediaId: Int) {
        viewModelScope.launch(context = Dispatchers.IO) {
            val seasonsList = DatabaseManager.instance.getEpisodeDao()
                .getSeasonsByMediaId(mediaId)
            _seasons.postValue(seasonsList)
            if (seasonsList.isNotEmpty()) {
                _currentSeason.postValue(seasonsList.first())
                val eps = DatabaseManager.instance.getEpisodeDao()
                    .getEpisodesBySeason(mediaId, seasonsList.first())
                _episodes.postValue(eps)
            }
        }
    }

    fun switchSeason(mediaId: Int, seasonNum: Int) {
        viewModelScope.launch(context = Dispatchers.IO) {
            _currentSeason.postValue(seasonNum)
            val eps = DatabaseManager.instance.getEpisodeDao()
                .getEpisodesBySeason(mediaId, seasonNum)
            _episodes.postValue(eps)
        }
    }
}
package com.xyoye.dandanplay.ui.main

import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.migration.ManualMigration
import kotlinx.coroutines.launch

/**
 * Created by xyoye on 2020/7/27.
 */

class MainViewModel : BaseViewModel() {
    fun initDatabase() {
        viewModelScope.launch {
            ManualMigration.migrate()
        }
    }
}
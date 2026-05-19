package com.xyoye.common_component.config

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object ViewModeSync {

    private val _gridViewChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val gridViewChanged: SharedFlow<Unit> = _gridViewChanged

    fun notifyGridViewChanged() {
        _gridViewChanged.tryEmit(Unit)
    }
}
package com.xyoye.common_component.bridge

import androidx.lifecycle.MutableLiveData

interface LoginObserver {
    fun getLoginLiveData(): MutableLiveData<Boolean>
}
package com.xyoye.common_component.utils

import androidx.lifecycle.MutableLiveData
import com.xyoye.common_component.config.UserConfig

object UserInfoHelper {

    val loginLiveData = MutableLiveData<Boolean>()

    fun login(token: String): Boolean {
        if (token.isNotEmpty()) {
            UserConfig.putUserToken(token)
            UserConfig.putUserLoggedIn(true)
            loginLiveData.postValue(true)
            return true
        }
        exitLogin()
        return false
    }

    fun exitLogin() {
        UserConfig.putUserToken("")
        UserConfig.putUserLoggedIn(false)
        loginLiveData.postValue(false)
    }

    fun isLoggedIn(): Boolean {
        return UserConfig.isUserLoggedIn()
    }
}
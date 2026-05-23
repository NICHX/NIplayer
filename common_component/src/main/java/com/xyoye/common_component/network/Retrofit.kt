package com.xyoye.common_component.network

import com.xyoye.common_component.network.config.Api
import com.xyoye.common_component.network.helper.AgentInterceptor
import com.xyoye.common_component.network.helper.DecompressInterceptor
import com.xyoye.common_component.network.helper.DynamicBaseUrlInterceptor
import com.xyoye.common_component.network.helper.LoggerInterceptor
import com.xyoye.common_component.network.service.AlistService
import com.xyoye.common_component.network.service.ExtendedService
import com.xyoye.common_component.utils.JsonHelper
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Created by xyoye on 2020/4/14.
 */

class Retrofit private constructor() {
    companion object {
        val extendedService: ExtendedService by lazy { Holder.instance.extendedService }

        val alistService: AlistService by lazy { Holder.instance.alistService }

        val downloadClient: OkHttpClient by lazy { Holder.instance.downloadClient }
    }

    private object Holder {
        val instance = Retrofit()
    }

    private val commonClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(AgentInterceptor())
            .addInterceptor(DecompressInterceptor())
            .addInterceptor(DynamicBaseUrlInterceptor())
            .addInterceptor(LoggerInterceptor().retrofit())
            .build()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            .addInterceptor(AgentInterceptor())
            .addInterceptor(DynamicBaseUrlInterceptor())
            .build()
    }

    private val moshiConverterFactory = MoshiConverterFactory.create(JsonHelper.MO_SHI)

    private val extendedService: ExtendedService by lazy {
        Retrofit.Builder()
            .addConverterFactory(moshiConverterFactory)
            .client(commonClient)
            .baseUrl(Api.PLACEHOLDER)
            .build()
            .create(ExtendedService::class.java)
    }

    private val alistService: AlistService by lazy {
        Retrofit.Builder()
            .addConverterFactory(moshiConverterFactory)
            .client(commonClient)
            .baseUrl(Api.PLACEHOLDER)
            .build()
            .create(AlistService::class.java)
    }
}
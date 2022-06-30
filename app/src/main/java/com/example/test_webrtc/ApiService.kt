package com.example.test_webrtc

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST

var SRS_SERVER_IP = "192.168.31.234"

interface ApiService {


    @POST("/rtc/v1/play/")
    suspend fun play(@Body body: SrsRequestBean): SrsResponseBean

    @POST("/rtc/v1/publish/")
    suspend fun publish(@Body body: SrsRequestBean): SrsResponseBean
}

private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

private val baseUrl
    get() = "http://$SRS_SERVER_IP:${1985}/"

private val retrofit
    get() = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

var apiService = retrofit.create<ApiService>()
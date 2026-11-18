package com.campusbite.data.remote

import com.campusbite.data.remote.dto.DishDto
import com.campusbite.data.remote.dto.StallDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CampusBite catalogue service — the live JSON resources published from this repository
 * (the api folder, served over HTTPS by the jsDelivr CDN). Read-only endpoints only.
 */
interface CampusBiteApi {
    @GET("stalls.json")
    suspend fun getStalls(): List<StallDto>

    @GET("menus.json")
    suspend fun getMenus(): List<DishDto>

    @GET("menus.json")
    suspend fun getMenusByStall(@Query("stallId") stallId: String): List<DishDto>

    @GET("menus/{id}.json")
    suspend fun getMenu(@Path("id") id: String): DishDto
}

package com.campusbite.data.remote

import com.campusbite.data.remote.dto.DishDto
import com.campusbite.data.remote.dto.StallDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** MockAPI — exactly 2 resources: /stalls and /menus. */
interface CampusBiteApi {
    @GET("stalls")
    suspend fun getStalls(): List<StallDto>

    @GET("menus")
    suspend fun getMenus(): List<DishDto>

    @GET("menus")
    suspend fun getMenusByStall(@Query("stallId") stallId: String): List<DishDto>

    @GET("menus/{id}")
    suspend fun getMenu(@Path("id") id: String): DishDto
}

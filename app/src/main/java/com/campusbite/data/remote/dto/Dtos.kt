package com.campusbite.data.remote.dto

import com.campusbite.data.local.entity.DishEntity
import com.campusbite.data.local.entity.StallEntity

data class StallDto(
    val id: String,
    val name: String?,
    val location: String?,
    val imageUrl: String?,
    val openHours: String?,
    val isOpen: Boolean?
)

data class DishDto(
    val id: String,
    val stallId: String,
    val name: String?,
    val category: String?,
    val price: Long?,
    val imageUrl: String?,
    val description: String?,
    val avgRating: Float?,
    val isAvailable: Boolean?
)

fun StallDto.toEntity(now: Long = System.currentTimeMillis()) = StallEntity(
    stallId = id,
    name = name.orEmpty(),
    location = location.orEmpty(),
    imageUrl = imageUrl.orEmpty(),
    openHours = openHours.orEmpty(),
    isOpen = isOpen ?: false,
    updatedAt = now
)

/** isFavourite is local-only: callers must preserve it (see DishDao.upsertKeepFavourite). */
fun DishDto.toEntity(now: Long = System.currentTimeMillis()) = DishEntity(
    dishId = id,
    stallId = stallId,
    name = name.orEmpty(),
    category = category.orEmpty(),
    price = price ?: 0L,
    imageUrl = imageUrl.orEmpty(),
    description = description.orEmpty(),
    avgRating = avgRating ?: 0f,
    isAvailable = isAvailable ?: true,
    isFavourite = false,
    updatedAt = now
)

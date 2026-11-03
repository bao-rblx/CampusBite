package com.campusbite.data.local

import androidx.room.TypeConverter
import com.campusbite.data.local.entity.OrderItemSnapshot
import com.campusbite.data.local.entity.OrderStatus
import com.campusbite.data.local.entity.SyncState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter fun orderStatusToString(v: OrderStatus): String = v.name
    @TypeConverter fun stringToOrderStatus(v: String): OrderStatus = OrderStatus.valueOf(v)

    @TypeConverter fun syncStateToString(v: SyncState): String = v.name
    @TypeConverter fun stringToSyncState(v: String): SyncState = SyncState.valueOf(v)

    companion object {
        private val gson = Gson()
        private val itemsType = object : TypeToken<List<OrderItemSnapshot>>() {}.type

        fun itemsToJson(items: List<OrderItemSnapshot>): String = gson.toJson(items)
        fun jsonToItems(json: String): List<OrderItemSnapshot> =
            gson.fromJson(json, itemsType) ?: emptyList()
    }
}

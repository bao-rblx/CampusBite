package com.campusbite.domain

import com.campusbite.data.local.entity.OrderItemSnapshot
import com.campusbite.data.local.entity.OrderStatus
import java.text.NumberFormat
import java.util.Locale

object OrderRules {
    fun total(items: List<OrderItemSnapshot>): Long {
        require(items.isNotEmpty()) { "Your cart is empty." }
        return items.fold(0L) { sum, item ->
            require(item.quantity in 1..99) { "Choose between 1 and 99 of each dish." }
            require(item.unitPrice >= 0) { "Invalid menu price." }
            Math.addExact(sum, Math.multiplyExact(item.unitPrice, item.quantity.toLong()))
        }
    }

    fun canCancel(status: OrderStatus) = status == OrderStatus.QUEUED
    fun remaining(capacity: Int, booked: Int) = (capacity - booked).coerceAtLeast(0)
}

fun money(amount: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN")).format(amount)

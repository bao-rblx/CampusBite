package com.campusbite.data

import com.campusbite.data.local.entity.*
import java.time.LocalDate
import java.time.ZoneId

object SeedData {
    val stalls = listOf(
        StallEntity("demo-rice", "Bếp Nhà", "Building A · Ground floor", "", "07:00–19:00", true, 1),
        StallEntity("demo-noodle", "Noodle Corner", "Building B · Courtyard", "", "07:00–19:00", true, 1),
        StallEntity("demo-cafe", "Campus Café", "Library · Ground floor", "", "07:00–19:00", true, 1)
    )
    private fun dish(id: String, stall: String, name: String, category: String, price: Long, description: String, rating: Float) =
        DishEntity("demo-$id", "demo-$stall", name, category, price, "", description, rating, true, false, 1)
    val dishes = listOf(
        dish("chicken", "rice", "Cơm gà nướng", "Rice", 35000, "Grilled lemongrass chicken, jasmine rice, cucumber and pickled vegetables. Contains soy and fish sauce.", 4.8f),
        dish("pork", "rice", "Cơm tấm sườn", "Rice", 38000, "Broken rice with grilled pork chop, scallion oil and fresh vegetables. Contains fish sauce and soy.", 4.9f),
        dish("tofu", "rice", "Cơm đậu hũ", "Rice", 25000, "Golden tofu, seasonal vegetables and steamed rice. Vegetarian. Contains soy.", 4.6f),
        dish("beef", "noodle", "Phở bò", "Noodles", 40000, "Slow-simmered beef broth, rice noodles, fresh herbs and tender beef. Contains fish sauce.", 4.8f),
        dish("bun", "noodle", "Bún thịt nướng", "Noodles", 35000, "Rice vermicelli with grilled pork, herbs and a tangy dressing. Contains peanuts and fish sauce.", 4.7f),
        dish("noodle", "noodle", "Mì xào rau củ", "Noodles", 28000, "Stir-fried egg noodles with crunchy seasonal vegetables. Contains wheat, egg and soy.", 4.5f),
        dish("coffee", "cafe", "Cà phê sữa đá", "Drinks", 18000, "Vietnamese iced coffee with condensed milk. Contains milk and caffeine.", 4.9f),
        dish("tea", "cafe", "Trà đào", "Drinks", 22000, "Peach iced tea with real peach slices. Contains caffeine.", 4.6f),
        dish("juice", "cafe", "Nước cam", "Drinks", 25000, "Freshly squeezed orange juice. No added sugar.", 4.7f),
        dish("rolls", "rice", "Gỏi cuốn", "Snacks", 20000, "Two fresh spring rolls with shrimp, greens and peanut dipping sauce. Contains shellfish and peanuts.", 4.7f),
        dish("bread", "cafe", "Bánh mì trứng", "Snacks", 20000, "Crisp baguette with egg, cucumber and herbs. Contains wheat and egg.", 4.8f),
        dish("yogurt", "cafe", "Sữa chua trái cây", "Snacks", 18000, "Creamy yogurt topped with seasonal fruit. Contains milk.", 4.6f)
    )
    fun slots(): List<SlotEntity> = (0L..2L).flatMap { offset ->
        val date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(offset)
        stalls.flatMap { stall ->
            (0..7).map { index ->
                val start = date.atTime(11, 30).plusMinutes(index * 15L)
                val end = start.plusMinutes(15)
                SlotEntity("${stall.stallId}-$date-$index", stall.stallId,
                    "$date · ${start.toLocalTime()}–${end.toLocalTime()}",
                    start.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli(), 20, 0)
            }
        }
    }
}

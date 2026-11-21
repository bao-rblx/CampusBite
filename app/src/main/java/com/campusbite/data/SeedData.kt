package com.campusbite.data

import com.campusbite.data.local.entity.*
import java.time.LocalDate
import java.time.ZoneId

object SeedData {
    val stalls = listOf(
        StallEntity("demo-rice", "Bếp Nhà", "Building A · Ground floor", "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=800&q=80", "07:00–19:00", true, 1),
        StallEntity("demo-noodle", "Noodle Corner", "Building B · Courtyard", "https://images.unsplash.com/photo-1552566626-52f8b828add9?auto=format&fit=crop&w=800&q=80", "07:00–19:00", true, 1),
        StallEntity("demo-cafe", "Campus Café", "Library · Ground floor", "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=800&q=80", "07:00–19:00", true, 1)
    )
    private fun dish(id: String, stall: String, name: String, category: String, price: Long, imageUrl: String, description: String, rating: Float) =
        DishEntity("demo-$id", "demo-$stall", name, category, price, imageUrl, description, rating, true, false, 1)
    val dishes = listOf(
        dish("chicken", "rice", "Cơm gà nướng", "Rice", 35000, "https://images.unsplash.com/photo-1598515214211-89d3c73ae83b?auto=format&fit=crop&w=800&q=80", "Grilled lemongrass chicken, jasmine rice, cucumber and pickled vegetables. Contains soy and fish sauce.", 4.8f),
        dish("pork", "rice", "Cơm tấm sườn", "Rice", 38000, "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=800&q=80", "Broken rice with grilled pork chop, scallion oil and fresh vegetables. Contains fish sauce and soy.", 4.9f),
        dish("tofu", "rice", "Cơm đậu hũ", "Rice", 25000, "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=800&q=80", "Golden tofu, seasonal vegetables and steamed rice. Vegetarian. Contains soy.", 4.6f),
        dish("beef", "noodle", "Phở bò", "Noodles", 40000, "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=800&q=80", "Slow-simmered beef broth, rice noodles, fresh herbs and tender beef. Contains fish sauce.", 4.8f),
        dish("bun", "noodle", "Bún thịt nướng", "Noodles", 35000, "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=800&q=80", "Rice vermicelli with grilled pork, herbs and a tangy dressing. Contains peanuts and fish sauce.", 4.7f),
        dish("noodle", "noodle", "Mì xào rau củ", "Noodles", 28000, "https://images.unsplash.com/photo-1612929633738-8fe44f7ec841?auto=format&fit=crop&w=800&q=80", "Stir-fried egg noodles with crunchy seasonal vegetables. Contains wheat, egg and soy.", 4.5f),
        dish("coffee", "cafe", "Cà phê sữa đá", "Drinks", 18000, "https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=800&q=80", "Vietnamese iced coffee with condensed milk. Contains milk and caffeine.", 4.9f),
        dish("tea", "cafe", "Trà đào", "Drinks", 22000, "https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=800&q=80", "Peach iced tea with real peach slices. Contains caffeine.", 4.6f),
        dish("juice", "cafe", "Nước cam", "Drinks", 25000, "https://images.unsplash.com/photo-1613478223719-2ab802602423?auto=format&fit=crop&w=800&q=80", "Freshly squeezed orange juice. No added sugar.", 4.7f),
        dish("rolls", "rice", "Gỏi cuốn", "Snacks", 20000, "https://images.unsplash.com/photo-1534422298391-e4f8c172dddb?auto=format&fit=crop&w=800&q=80", "Two fresh spring rolls with shrimp, greens and peanut dipping sauce. Contains shellfish and peanuts.", 4.7f),
        dish("bread", "cafe", "Bánh mì trứng", "Snacks", 20000, "https://images.unsplash.com/photo-1626804475297-41608e074eb1?auto=format&fit=crop&w=800&q=80", "Crisp baguette with egg, cucumber and herbs. Contains wheat and egg.", 4.8f),
        dish("yogurt", "cafe", "Sữa chua trái cây", "Snacks", 18000, "https://images.unsplash.com/photo-1488477181946-6428a0291777?auto=format&fit=crop&w=800&q=80", "Creamy yogurt topped with seasonal fruit. Contains milk.", 4.6f)
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

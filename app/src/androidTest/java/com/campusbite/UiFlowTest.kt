package com.campusbite

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.campusbite.di.ServiceLocator
import com.campusbite.data.local.entity.OrderStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private var scenario: ActivityScenario<com.campusbite.ui.MainActivity>? = null
    @After fun closeActivity() { scenario?.close() }
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun text(value: String): UiObject2 {
        device.waitForIdle()
        android.os.SystemClock.sleep(400)
        device.findObject(By.text(value))?.let { return it }
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            repeat(10) {
                device.findObject(By.text(value))?.let { return it }
                val scroll = device.findObject(By.scrollable(true))
                if (scroll != null) runCatching { scroll.scroll(direction, 0.7f) }
                device.waitForIdle()
            }
        }
        capture("failure")
        device.dumpWindowHierarchy(File(context.cacheDir, "failure.xml"))
        return requireNotNull(device.wait(Until.findObject(By.text(value)), 5000)) { "Missing UI text: $value" }
    }
    private fun click(value: String) {
        repeat(4) {
            try { text(value).click(); device.waitForIdle(); return }
            catch (e: StaleObjectException) { if (it == 3) throw e }
        }
    }
    private fun tab(id: String) {
        device.wait(Until.gone(By.res("com.campusbite", "snackbar_text")), 6000)
        requireNotNull(device.wait(Until.findObject(By.res("com.campusbite", id)), 5000)).click()
        device.waitForIdle()
    }
    private fun enterText(selector: BySelector, value: String) {
        repeat(4) {
            device.waitForIdle()
            try {
                requireNotNull(device.wait(Until.findObject(selector), 5000)).text = value
                return
            } catch (e: StaleObjectException) { if (it == 3) throw e }
        }
    }
    private fun capture(name: String) {
        device.executeShellCommand("mkdir -p /sdcard/Download/CampusBite-QA")
        device.executeShellCommand("screencap -p /sdcard/Download/CampusBite-QA/$name.png")
    }
    @Test fun studentJourney() {
        runBlocking {
            ServiceLocator.repository.initializeDemo()
            ServiceLocator.auth.demo()
            val uid = ServiceLocator.auth.uid.value!!
            ServiceLocator.database.cartDao().clear(uid)
            ServiceLocator.database.orderDao().deleteForUser(uid)
            ServiceLocator.database.reviewDao().deleteForUser(uid)
            ServiceLocator.auth.logout()
        }
        scenario = ActivityScenario.launch(com.campusbite.ui.MainActivity::class.java)
        click("Explore the demo")
        text("What sounds good?")
        capture("01-home")
        tab("searchFragment")
        val search = requireNotNull(device.wait(Until.findObject(By.res("com.campusbite", "search")), 5000))
        search.click(); search.text = "Cơm tấm"; device.waitForIdle()
        click("Cơm tấm sườn")
        capture("02-dish")
        click("Add to cart")
        device.wait(Until.findObject(By.text("Added to your cart.")), 5000)
        device.wait(Until.gone(By.text("Added to your cart.")), 6000)
        tab("cartFragment")
        assertNotNull(text("Cơm tấm sườn"))
        capture("03-cart")
        val checkout = requireNotNull(device.wait(Until.findObject(By.res("com.campusbite", "primary")), 5000))
        android.os.SystemClock.sleep(3000)
        checkout.click(); device.waitForIdle()
        capture("04-after-checkout-click")
        device.dumpWindowHierarchy(File(context.cacheDir, "checkout.xml"))
        text("Place order")
        capture("04-pickup")
        click("Place order")
        assertNotNull(device.wait(Until.findObject(By.text("A good lunch is on the way.")), 10000))
        capture("05-order")
        click("Demo: advance order status")
        click("Demo: advance order status")
        click("Demo: advance order status")
        assertNotNull(text("Your food is ready!"))
        capture("06-ready")
        click("Demo: advance order status")
        click("Order again")
        assertNotNull(device.wait(Until.findObject(By.text("Added to your cart at current prices.")), 5000))
        tab("cartFragment")
        requireNotNull(device.wait(Until.findObject(By.res("com.campusbite", "primary")), 5000)).click()
        click("Place order")
        click("Cancel order")
        requireNotNull(device.wait(Until.findObject(By.res("android", "button1")), 5000)).click()
        assertNotNull(text("Order cancelled"))
        click("Remove from history")
        assertTrue(device.wait(Until.gone(By.text("Remove from history")), 5000))
        tab("searchFragment")
        requireNotNull(device.wait(Until.findObject(By.res("com.campusbite", "search")), 5000)).apply { click(); text = "Cơm tấm" }; device.waitForIdle()
        click("Cơm tấm sườn")
        click("Write a review")
        text("Your review")
        enterText(By.clazz("android.widget.EditText"), "Fresh, tasty and ready after class.")
        click("Save review")
        assertNotNull(text("Fresh, tasty and ready after class."))
        capture("07-review")
        tab("profileFragment")
        click("Toggle dark mode")
        assertNotNull(text("A seat at the table."))
        capture("08-dark-profile")
        click("Toggle dark mode")
        tab("homeFragment")
        runBlocking {
            val orders = ServiceLocator.database.orderDao().observeAll(ServiceLocator.auth.uid.value!!).first()
            assertEquals(1, orders.size)
            assertEquals(OrderStatus.COMPLETED, orders.single().status)
            assertEquals(1, ServiceLocator.database.reviewDao().observeForDish("demo-pork").first().size)
        }
    }
}

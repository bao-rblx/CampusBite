package com.campusbite.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.campusbite.R
import com.campusbite.data.local.entity.*
import com.campusbite.databinding.FragmentDashboardBinding
import com.campusbite.domain.money
import com.campusbite.viewmodel.CampusState
import com.campusbite.viewmodel.CampusViewModel
import com.campusbite.viewmodel.UiState
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding
    private val vm: CampusViewModel by activityViewModels()
    private lateinit var adapter: CampusAdapter
    private var latest = CampusState()
    private var catalogState: UiState<Unit> = UiState.Loading
    private val page get() = arguments?.getString("page") ?: "home"
    private var query = ""
    private var category = "All"
    private var favourites = false
    private var budget = false
    private var sort = 0
    private var status = "All"
    private var visibleCategories: List<String> = emptyList()
    private var activeDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        query = state?.getString("query").orEmpty(); category = state?.getString("category") ?: "All"
        favourites = state?.getBoolean("favourites") ?: false; budget = state?.getBoolean("budget") ?: false
        sort = state?.getInt("sort") ?: 0; status = state?.getString("status") ?: "All"
        return FragmentDashboardBinding.inflate(inflater, container, false).also { _binding = it }.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = requireNotNull(binding)
        adapter = CampusAdapter(::handle)
        val manager = GridLayoutManager(requireContext(), 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int) = if (adapter.currentList.getOrNull(position)?.dish != null) 1 else 2 }
        }
        b.list.layoutManager = manager; b.list.adapter = adapter
        b.list.setHasFixedSize(false)
        b.list.setItemViewCacheSize(20)
        b.searchBox.show(page == "home" || page == "search")
        b.search.setText(query)
        b.search.doAfterTextChanged { query = it.toString(); render() }
        b.refresh.setOnRefreshListener { vm.loadCatalog(); binding?.refresh?.isRefreshing = false }
        b.refresh.isEnabled = page == "home" || page == "search"
        b.primary.addSpringTouchEffect()
        if (page == "cart") ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, h: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun getSwipeDirs(r: RecyclerView, h: RecyclerView.ViewHolder): Int = if (adapter.currentList.getOrNull(h.bindingAdapterPosition)?.id?.startsWith("cart:") == true) super.getSwipeDirs(r, h) else 0
            override fun onSwiped(h: RecyclerView.ViewHolder, direction: Int) {
                adapter.currentList.getOrNull(h.bindingAdapterPosition)?.id?.let { remove(it) }
            }
        }).attachToRecyclerView(b.list)
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.state.collect { latest = it; render() } }
            launch { vm.busy.collect { binding?.primary?.isEnabled = !it } }
            launch { vm.catalogState.collect { catalogState = it; render() } }
        } }
        filters()
        if (page == "home" || page == "search") vm.loadCatalog()
    }

    private fun filters() {
        val b = binding ?: return
        b.filters.removeAllViews()
        fun chip(text: String, checked: Boolean, click: () -> Unit) {
            b.filters.addView(Chip(requireContext()).apply {
                this.text = text; isCheckable = true; isChecked = checked; setEnsureMinTouchTargetSize(true)
                setOnClickListener { click(); filters(); render() }
            })
        }
        when (page) {
            "home", "search" -> {
                (listOf("All") + latest.dishes.map { it.category }.distinct().sorted()).forEach { value ->
                    val label = when (value) {
                        "Rice" -> "🍚 Rice"
                        "Noodles" -> "🍜 Noodles"
                        "Drinks" -> "🥤 Drinks"
                        "Snacks" -> "🍿 Snacks"
                        else -> value
                    }
                    chip(label, category == value) { category = value }
                }
                chip("♥ Saved", favourites) { favourites = !favourites }
                chip("≤ 30,000 ₫", budget) { budget = !budget }
                chip(listOf("⭐ Top rated", "Price ↑", "Price ↓")[sort], sort != 0) { sort = (sort + 1) % 3 }
            }
            "orders" -> listOf("All", "QUEUED", "CONFIRMED", "PREPARING", "READY", "COMPLETED", "CANCELLED").forEach { value -> chip(value.lowercase().replaceFirstChar { it.titlecase() }, status == value) { status = value } }
        }
        b.filterScroll.show(page in listOf("home", "search", "orders") && (latest.user?.role ?: "STUDENT") == "STUDENT")
    }

    private fun render() {
        val b = binding ?: return
        val role = latest.user?.role ?: "STUDENT"
        b.primary.show(false)
        b.loading.show(false)
        b.errorBanner.show(false)

        if ((page == "home" || page == "search") && role == "STUDENT") {
            when (val catalog = catalogState) {
                is UiState.Loading -> { b.loading.show(true); adapter.submitList(emptyList()); return }
                is UiState.Error -> { b.errorBanner.text = catalog.message; b.errorBanner.show(true) }
                is UiState.Success -> Unit
            }
        }

        val categories = latest.dishes.map { it.category }.distinct().sorted()
        if (categories != visibleCategories) { visibleCategories = categories; filters() }
        val rows = mutableListOf<DisplayRow>()
        fun stall(id: String) = latest.stalls.find { it.stallId == id }?.name ?: "Campus stall"

        when (role) {
            "VENDOR" -> renderVendorView(b, rows)
            "MANAGER" -> renderManagerView(b, rows)
            else -> renderStudentView(b, rows, stall = ::stall)
        }
        adapter.submitList(rows)
    }

    private fun renderVendorView(b: FragmentDashboardBinding, rows: MutableList<DisplayRow>) {
        b.eyebrow.text = "CAMPUSBITE  ✦  VENDOR FULFILLMENT"
        b.title.text = "Stall Operations Center"
        b.subtitle.text = "Manage live incoming orders and menu item availability."
        b.searchBox.show(false)

        rows += DisplayRow(
            "role_info",
            latest.user?.displayName ?: "Stall Vendor",
            latest.user?.email.orEmpty(),
            "VENDOR ACCOUNT",
            listOf(RowAction("Sign out", "logout")),
            hero = true
        )

        val vendorOrders = latest.allOrders.filter { it.status != OrderStatus.CANCELLED }
        val completedRevenue = vendorOrders.filter { it.status == OrderStatus.COMPLETED }.sumOf { it.totalAmount }
        rows += DisplayRow(
            "vendor_stats",
            "Fulfillment Overview",
            "Completed Sales: ${money(completedRevenue)}\nActive Orders: ${vendorOrders.count { it.status != OrderStatus.COMPLETED }} · Total: ${vendorOrders.size}",
            "LIVE SUMMARY"
        )

        rows += DisplayRow("header_orders", "Incoming Order Queue", "Real-time orders ready for preparation and pickup.", "FULFILLMENT QUEUE")
        if (vendorOrders.isEmpty()) {
            rows += DisplayRow("empty_vendor_orders", "No active orders queued.", "New customer orders will appear here in real-time.")
        } else {
            vendorOrders.forEach { order ->
                val stallName = latest.stalls.find { it.stallId == order.stallId }?.name ?: "Stall"
                val actions = when (order.status) {
                    OrderStatus.QUEUED -> listOf(RowAction("Confirm Order ➔", "confirmOrder"))
                    OrderStatus.CONFIRMED -> listOf(RowAction("Start Preparing 🍳", "prepareOrder"))
                    OrderStatus.PREPARING -> listOf(RowAction("Ready for Pickup 🔔", "readyOrder"))
                    OrderStatus.READY -> listOf(RowAction("Complete Pickup ✔", "completeOrder"))
                    else -> emptyList()
                }
                rows += DisplayRow(
                    order.orderId,
                    "$stallName · #${order.orderId.take(8)}",
                    "Pickup Window: ${order.pickupWindow}\nTotal: ${money(order.totalAmount)} · Status: ${order.status.name}",
                    order.status.name.replace('_', ' '),
                    actions,
                    hero = order.status == OrderStatus.QUEUED || order.status == OrderStatus.PREPARING
                )
            }
        }

        rows += DisplayRow("header_inventory", "Dish Availability Manager", "Toggle sold-out items instantly on the campus menu.", "INVENTORY CONTROL")
        latest.dishes.forEach { dish ->
            val stallName = latest.stalls.find { it.stallId == dish.stallId }?.name ?: "Stall"
            rows += DisplayRow(
                "dish_avail:${dish.dishId}",
                dish.name,
                "$stallName · ${money(dish.price)}\nStatus: ${if (dish.isAvailable) "Available" else "Sold Out"}",
                if (dish.isAvailable) "AVAILABLE" else "SOLD OUT",
                listOf(RowAction(if (dish.isAvailable) "Mark Sold Out" else "Mark Available", "toggleDishAvailability"))
            )
        }
    }

    private fun renderManagerView(b: FragmentDashboardBinding, rows: MutableList<DisplayRow>) {
        b.eyebrow.text = "CAMPUSBITE  ✦  EXECUTIVE DASHBOARD"
        b.title.text = "Campus Operations Analytics"
        b.subtitle.text = "Real-time revenue analytics, stall operational controls, and ratings."
        b.searchBox.show(false)

        rows += DisplayRow(
            "role_info",
            latest.user?.displayName ?: "Campus Manager",
            latest.user?.email.orEmpty(),
            "MANAGER ACCOUNT",
            listOf(RowAction("Sign out", "logout")),
            hero = true
        )

        val totalRevenue = latest.allOrders.filter { it.status == OrderStatus.COMPLETED }.sumOf { it.totalAmount }
        val totalOrders = latest.allOrders.size
        val avgRating = if (latest.dishes.isEmpty()) 4.8f else latest.dishes.map { it.avgRating }.average().toFloat()
        val openStalls = latest.stalls.count { it.isOpen }

        rows += DisplayRow(
            "manager_analytics",
            "Campus Revenue: ${money(totalRevenue)}",
            "Total Orders: $totalOrders · Average Campus Rating: ★ ${"%.1f".format(avgRating)}\nStalls Open: $openStalls of ${latest.stalls.size}",
            "EXECUTIVE SUMMARY",
            hero = true
        )

        rows += DisplayRow("header_stalls", "Stall Operational Controls", "Monitor stall sales performance and toggle open status.", "STALL MANAGEMENT")
        latest.stalls.forEach { stall ->
            val stallOrders = latest.allOrders.filter { it.stallId == stall.stallId }
            val stallRev = stallOrders.filter { it.status == OrderStatus.COMPLETED }.sumOf { it.totalAmount }
            rows += DisplayRow(
                "stall_manage:${stall.stallId}",
                stall.name,
                "${stall.location}\nRevenue: ${money(stallRev)} · Orders: ${stallOrders.size} · Status: ${if (stall.isOpen) "Open" else "Closed"}",
                if (stall.isOpen) "OPEN" else "CLOSED",
                listOf(RowAction(if (stall.isOpen) "Close Stall" else "Open Stall", "toggleStallOpen"))
            )
        }

        rows += DisplayRow("header_feedback", "Live Campus Feedback Feed", "Real-time student reviews and meal ratings.", "CUSTOMER FEEDBACK")
        if (latest.allReviews.isEmpty()) {
            rows += DisplayRow("empty_reviews", "No student reviews yet.", "Reviews submitted by students will stream live here.")
        } else {
            latest.allReviews.take(10).forEach { review ->
                val dishName = latest.dishes.find { it.dishId == review.dishId }?.name ?: "Dish"
                rows += DisplayRow(
                    "rev:${review.reviewId}",
                    "${"★".repeat(review.rating)}  ${review.authorName}",
                    "Item: $dishName\n\"${review.comment.ifBlank { "Rated ${review.rating} out of 5" }}\"",
                    "RATING ${review.rating}/5"
                )
            }
        }
    }

    private fun renderStudentView(b: FragmentDashboardBinding, rows: MutableList<DisplayRow>, stall: (String) -> String) {
        b.eyebrow.text = "CAMPUSBITE  ✦  CAMPUS DINING"
        when (page) {
            "home", "search" -> {
                b.title.text = if (page == "home") "What sounds good?" else "Find your next favourite."
                b.subtitle.text = if (page == "home") "Hey ${latest.user?.displayName?.substringBefore(' ') ?: "there"}. Make your lunch break count." else "Fresh picks. Student prices. Right on campus."
                if (page == "home" && query.isBlank() && category == "All" && !favourites && !budget) {
                    rows += DisplayRow("hero", "Your lunch break,\nwithout the wait.", "Order ahead. Pick up after class.\nMeals from 18,000 ₫ · Pay at pickup", "ZERO QUEUES · FAST PICKUP", listOf(RowAction("Explore the stalls ↗", "stalls")), hero = true)
                }
                val dishes = latest.dishes.filter {
                    (category == "All" || it.category == category) && (!favourites || it.isFavourite) && (!budget || it.price <= 30000) &&
                        (query.isBlank() || it.name.contains(query, true) || stall(it.stallId).contains(query, true))
                }.let { list -> when (sort) { 1 -> list.sortedBy { it.price }; 2 -> list.sortedByDescending { it.price }; else -> list.sortedByDescending { it.avgRating } } }
                if (dishes.isEmpty()) rows += DisplayRow("empty", "Nothing on the menu here yet.", if (latest.dishes.isEmpty()) "Pull down to refresh the menu. Cached dishes will appear here even when you are offline." else "Try another search or change the filters.", actions = listOf(RowAction("Reset filters", "reset")))
                else rows += dishes.map { DisplayRow(it.dishId, dish = it, stall = stall(it.stallId)) }
            }
            "cart" -> {
                b.title.text = "Your lunch, lined up."
                b.subtitle.text = "${latest.cart.sumOf { it.quantity }} items · Swipe an item to remove it"
                if (latest.cart.isEmpty()) rows += DisplayRow("empty", "Something delicious is missing.", "Add a campus favourite and make your next break a good one.", actions = listOf(RowAction("Browse the menu", "home")), hero = true)
                latest.cart.groupBy { it.stallId }.forEach { (stallId, items) ->
                    rows += DisplayRow("stall:$stallId", stall(stallId), latest.stalls.find { it.stallId == stallId }?.location.orEmpty(), "PICKUP STALL")
                    items.forEach { item ->
                        val name = latest.dishes.find { it.dishId == item.dishId }?.name ?: "Dish"
                        rows += DisplayRow("cart:${item.cartItemId}", name, "${money(item.unitPrice)} each${item.note?.takeIf { it.isNotBlank() }?.let { "\nNote: $it" }.orEmpty()}", "${item.quantity} ×  ·  ${money(item.unitPrice * item.quantity)}",
                            listOf(RowAction("−", "minus"), RowAction("${item.quantity}  +", "plus"), RowAction("Remove", "remove")))
                    }
                }
                if (latest.cart.isNotEmpty()) {
                    val total = latest.cart.sumOf { it.quantity * it.unitPrice }
                    rows += DisplayRow("total", money(total), "No delivery fee. Pay at the stall when you collect your food.", "TOTAL", hero = true)
                    b.primary.show(true); b.primary.text = "Choose pickup time · ${money(total)}"; b.primary.setOnClickListener { checkout() }
                }
            }
            "orders" -> {
                b.title.text = "Lunch plans, sorted."
                b.subtitle.text = "Your pickup updates and order status, all in one place."
                val orders = latest.orders.filter { status == "All" || it.status.name == status }
                if (orders.isEmpty()) rows += DisplayRow("empty", "No orders here yet.", "Your next great lunch starts with the menu.", actions = listOf(RowAction("Find something good", "home")))
                orders.forEach { order ->
                    val sync = when (order.syncState) { SyncState.PENDING, SyncState.SYNCING -> " · Waiting to sync"; SyncState.FAILED -> " · Pickup unavailable"; else -> "" }
                    rows += DisplayRow(order.orderId, stall(order.stallId), "${order.pickupWindow}\n${money(order.totalAmount)} · #${order.orderId.take(8)}$sync", order.status.name.replace('_', ' '), listOf(RowAction("View order →", "order"), RowAction("Order again", "reorder")), hero = order.status == OrderStatus.READY)
                }
            }
            "profile" -> {
                b.title.text = "A seat at the table."
                b.subtitle.text = "Your profile, your preferences."
                val user = latest.user
                rows += DisplayRow("profile", user?.displayName ?: "Welcome", user?.email.orEmpty(), "STUDENT ACCOUNT", listOf(RowAction("Edit profile", "editProfile")))
                rows += DisplayRow("saved", "The usual, please.", "${latest.dishes.count { it.isFavourite }} saved favourites", "YOUR COLLECTION", listOf(RowAction("See favourites", "saved")))
                rows += DisplayRow("settings", "Make it yours.", "Favourite stall: ${user?.favouriteStallId?.let(stall) ?: "Not selected"}\nNotification preference: ${if (user?.notificationsOn == true) "On" else "Off"}", "PREFERENCES", listOf(RowAction("Toggle dark mode", "theme"), RowAction("Browse stalls", "stalls")))
                rows += DisplayRow("session", "Account & privacy", "Signing out clears this account’s cart. Saved orders remain available when you sign in again.", actions = listOf(RowAction("Sign out", "logout"), RowAction("Delete account", "deleteAccount")))
            }
        }
    }

    private fun handle(action: String, id: String) {
        val dish = latest.dishes.find { it.dishId == id }
        val order = latest.orders.find { it.orderId == id }
        val item = latest.cart.find { "cart:${it.cartItemId}" == id }
        when (action) {
            "dish", "order" -> findNavController().navigate(R.id.detailFragment, bundleOf("kind" to action, "id" to id))
            "add" -> vm.action("Added to your cart.") { vm.add(id) }
            "favourite" -> if (dish != null) vm.action { vm.favourite(dish) }
            "minus" -> if (item != null) if (item.quantity == 1) remove(id) else vm.action { vm.quantity(item, item.quantity - 1) }
            "plus" -> if (item != null) vm.action { vm.quantity(item, item.quantity + 1) }
            "remove" -> remove(id)
            "reorder" -> if (order != null) vm.action("Added to your cart at current menu prices.") { vm.reorder(order) }
            "home" -> (activity as? MainActivity)?.selectTab(R.id.homeFragment)
            "saved" -> { (activity as? MainActivity)?.selectTab(R.id.searchFragment, true) }
            "reset" -> { category = "All"; query = ""; favourites = false; budget = false; binding?.search?.setText(""); filters(); render() }
            "stalls" -> showStalls()
            "editProfile" -> editProfile()
            "roleStudent" -> vm.action("Switched to Student mode.") { vm.saveRole("STUDENT") }
            "roleVendor" -> vm.action("Switched to Vendor mode.") { vm.saveRole("VENDOR") }
            "roleManager" -> vm.action("Switched to Manager mode.") { vm.saveRole("MANAGER") }
            "confirmOrder" -> vm.action("Order confirmed.") { vm.updateOrderStatus(id, OrderStatus.CONFIRMED) }
            "prepareOrder" -> vm.action("Order in preparation.") { vm.updateOrderStatus(id, OrderStatus.PREPARING) }
            "readyOrder" -> vm.action("Order ready for pickup!") { vm.updateOrderStatus(id, OrderStatus.READY) }
            "completeOrder" -> vm.action("Order pickup completed.") { vm.updateOrderStatus(id, OrderStatus.COMPLETED) }
            "toggleDishAvailability" -> {
                val dishId = id.removePrefix("dish_avail:")
                val targetDish = latest.dishes.find { it.dishId == dishId }
                if (targetDish != null) vm.action("Dish availability updated.") { vm.setDishAvailability(dishId, !targetDish.isAvailable) }
            }
            "toggleStallOpen" -> {
                val stallId = id.removePrefix("stall_manage:")
                val targetStall = latest.stalls.find { it.stallId == stallId }
                if (targetStall != null) vm.action("Stall status updated.") { vm.setStallOpenStatus(stallId, !targetStall.isOpen) }
            }
            "theme" -> {
                val prefs = requireContext().getSharedPreferences("settings", 0)
                val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                prefs.edit().putBoolean("dark", !dark).apply()
                AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
            }
            "logout" -> confirm("Sign out?", "Your current cart will be cleared.") { vm.submit { vm.logout() } }
            "deleteAccount" -> confirm("Delete your account?", "This permanently removes this login and its data from this device. For a connected account, server data cleanup must be enabled in Firebase. You may need to sign in again before deletion.") { vm.submit { vm.deleteAccount() } }
        }
    }
    fun showSaved() { favourites = true; filters(); render() }
    private fun remove(id: String) {
        val item = latest.cart.find { "cart:${it.cartItemId}" == id } ?: return
        vm.action {
            vm.quantity(item, 0)
            binding?.root?.let { Snackbar.make(it, "Item removed", Snackbar.LENGTH_LONG).setAction("Undo") { vm.action { vm.restore(item) } }.show() }
        }
    }
    private fun confirm(title: String, text: String, action: () -> Unit) {
        activeDialog = MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(text).setNegativeButton("Keep it", null).setPositiveButton("Continue") { _, _ -> action() }.show()
    }
    private fun form(): LinearLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(22), context.dp(6), context.dp(22), context.dp(8)) }
    private fun showStalls() {
        val box = form()
        latest.stalls.forEach { s -> box.label(s.name, 20f, true); box.label("${s.location}\n${s.openHours} · ${if (s.isOpen) "Open" else "Closed"}", 14f) }
        activeDialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Around campus").setView(ScrollView(requireContext()).apply { addView(box) }).setPositiveButton("Done", null).show()
    }
    private fun editProfile() {
        val user = latest.user ?: return
        val box = form(); val name = box.field("Display name", user.displayName)
        box.label("Favourite stall", 14f, true)
        val stallIds = listOf<String?>(null) + latest.stalls.map { it.stallId }
        val spinner = Spinner(requireContext()).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("No preference") + latest.stalls.map { it.name }); setSelection(stallIds.indexOf(user.favouriteStallId).coerceAtLeast(0)); minimumHeight = context.dp(48) }
        box.addView(spinner)
        val notifications = MaterialSwitch(requireContext()).apply { text = "Notification preference"; isChecked = user.notificationsOn; minHeight = context.dp(48) }; box.addView(notifications)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Your profile").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            if (name.value.trim().length < 2) name.layout.error = "Enter at least two characters" else vm.submit("Profile saved.") { vm.saveProfile(name.value, stallIds[spinner.selectedItemPosition], notifications.isChecked); dialog.dismiss() }
        } }
        activeDialog = dialog; dialog.show()
    }
    private fun checkout() {
        val box = form()
        box.label("Pay at pickup stall · No delivery fee", 14f, true)
        val selections = mutableMapOf<String, String>()
        var allHaveSlots = true
        latest.cart.groupBy { it.stallId }.forEach { (stallId, _) ->
            box.label(latest.stalls.find { it.stallId == stallId }?.name.orEmpty(), 18f, true)
            val slots = latest.slots.filter { it.stallId == stallId && it.startsAt > System.currentTimeMillis() && it.bookedCount < it.capacity }
            if (slots.isEmpty()) { box.label("No pickup times available. Refresh or try again later."); allHaveSlots = false }
            else {
                selections[stallId] = slots.first().slotId
                box.addView(Spinner(requireContext()).apply {
                    minimumHeight = context.dp(56)
                    adapter = ArrayAdapter(context, R.layout.item_pickup_slot, slots.map { "${it.label}\n${it.capacity - it.bookedCount} places left" })
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selections[stallId] = slots[position].slotId }
                    }
                })
            }
        }
        box.label("Total: ${money(latest.cart.sumOf { it.quantity * it.unitPrice })}", 22f, true)
        box.label("One order per stall. If you are offline, a live order waits for connection before a pickup time is reserved.", 13f)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("When shall we see you?").setView(ScrollView(requireContext()).apply { addView(box) }).setNegativeButton("Back", null).setPositiveButton("Place order", null).create()
        dialog.setOnShowListener {
            dialog.getButton(-1).isEnabled = allHaveSlots
            dialog.getButton(-1).setOnClickListener { vm.submit {
                val orders = vm.checkout(selections.toMap())
                dialog.dismiss()
                if (isAdded && binding != null) findNavController().navigate(R.id.detailFragment, bundleOf("kind" to "order", "id" to orders.first()))
                vm.message("Order placed successfully! Pickup details confirmed.")
            } }
        }
        activeDialog = dialog; dialog.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query); outState.putString("category", category); outState.putBoolean("favourites", favourites)
        outState.putBoolean("budget", budget); outState.putInt("sort", sort); outState.putString("status", status)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() { activeDialog?.dismiss(); activeDialog = null; binding?.list?.adapter = null; _binding = null; super.onDestroyView() }
}

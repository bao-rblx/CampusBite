package com.campusbite.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.campusbite.R
import com.campusbite.data.local.entity.*
import com.campusbite.databinding.FragmentFormBinding
import com.campusbite.domain.money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class DetailFragment : Fragment() {
    private var _binding: FragmentFormBinding? = null
    private val binding get() = _binding
    private val vm: CampusViewModel by activityViewModels()
    private val id get() = requireArguments().getString("id").orEmpty()
    private val kind get() = requireArguments().getString("kind") ?: "dish"
    private var state = CampusState()
    private var reviews: List<ReviewEntity> = emptyList()
    private var quantity = 1
    private var note = ""
    private var listener: ListenerRegistration? = null
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private var renderedKey: Any? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        quantity = savedInstanceState?.getInt("quantity", 1) ?: 1; note = savedInstanceState?.getString("note").orEmpty()
        return FragmentFormBinding.inflate(inflater, container, false).also { _binding = it }.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.state.collect { state = it; render() } }
            if (kind == "dish") launch { vm.reviewsFor(id).collect { reviews = it; render() } }
        } }
    }
    override fun onStart() { super.onStart(); if (kind == "dish") listener = vm.watchReviews(id) }
    override fun onStop() { listener?.remove(); listener = null; super.onStop() }
    private fun render() {
        val box = binding?.content ?: return
        val key = if (kind == "dish") listOf(state.dishes.find { it.dishId == id }, reviews, state.user?.uid)
            else listOf(state.orders.find { it.orderId == id }, state.stalls)
        if (key == renderedKey) return
        renderedKey = key
        val scroll = binding?.root as? ScrollView
        val position = scroll?.scrollY ?: 0
        box.removeAllViews()
        box.button("← Back") { findNavController().popBackStack() }
        if (kind == "dish") renderDish(box) else renderOrder(box)
        scroll?.post { scroll.scrollTo(0, position) }
    }
    private fun renderDish(box: LinearLayout) {
        val dish = state.dishes.find { it.dishId == id } ?: return
        val photo = ImageView(requireContext()).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = dish.name }
        box.addView(photo, ViewGroup.LayoutParams(-1, requireContext().dp(220)))
        // Shared Glide loader: placeholder, error fallback and disk/memory cache.
        ImageLoader.load(photo, dish.imageUrl, dish.category)
        box.label("${dish.category.uppercase()}  ·  ${state.stalls.find { it.stallId == dish.stallId }?.name.orEmpty()}", 12f, true)
        box.label(dish.name, 30f, true)
        val rating = if (reviews.isEmpty()) dish.avgRating.toDouble() else reviews.map { it.rating }.average()
        box.label("${money(dish.price)}  ·  ★ ${"%.1f".format(rating)}", 22f, true)
        box.label("Ingredients & allergens", 17f, true); box.label(dish.description, 15f)
        box.button(if (dish.isFavourite) "♥ Saved to favourites" else "♡ Save to favourites") { vm.action { vm.favourite(dish) } }
        box.label("How many?", 15f, true)
        box.addView(Spinner(requireContext()).apply {
            minimumHeight = context.dp(48)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, (1..20).map { "$it ${if (it == 1) "portion" else "portions"}" })
            setSelection(quantity - 1)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, rowId: Long) { quantity = position + 1 }
            }
        })
        box.field("Note for the stall (optional)", note).edit.doAfterTextChanged { note = it.toString().take(200) }
        box.button(if (dish.isAvailable) "Add to cart" else "Sold out") { vm.action("Added to your cart.") { vm.add(id, quantity, note) } }.isEnabled = dish.isAvailable
        box.button("Pre-order →") { vm.action { vm.add(id, quantity, note); if (isAdded) (activity as? MainActivity)?.selectTab(R.id.cartFragment) } }.isEnabled = dish.isAvailable
        box.label("From the campus community", 23f, true)
        box.button(if (reviews.any { it.userId == vm.uid.value }) "Edit your review" else "Write a review") { editReview() }
        if (reviews.isEmpty()) box.label("Be the first to share a bite-sized review.", 14f)
        reviews.forEach { review ->
            box.label("${"★".repeat(review.rating)}  ${review.authorName}", 16f, true)
            box.label(review.comment.ifBlank { "Rated ${review.rating} out of 5" }, 14f)
            if (review.userId == vm.uid.value) box.button("Delete your review") {
                dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Delete this review?").setNegativeButton("Keep", null).setPositiveButton("Delete") { _, _ -> vm.action("Review deleted.") { vm.review(id, review.rating, review.comment, true) } }.show()
            }
        }
    }
    private fun renderOrder(box: LinearLayout) {
        val order = state.orders.find { it.orderId == id }
        if (order == null) { box.label("Loading your order…", 24f, true); return }
        box.label(if (vm.isDemo) "DEMO ORDER · #${id.take(8)}" else "ORDER #${id.take(8)}", 12f, true)
        box.label(when { order.syncState == SyncState.FAILED -> "Pickup unavailable"; order.status == OrderStatus.READY -> "Your food is ready!"; order.status == OrderStatus.CANCELLED -> "Order cancelled"; else -> "A good lunch is on the way." }, 30f, true)
        box.label(state.stalls.find { it.stallId == order.stallId }?.let { "${it.name}\n${it.location}" }.orEmpty(), 18f, true)
        box.label("Pickup: ${order.pickupWindow}", 16f)
        if (order.syncState == SyncState.PENDING || order.syncState == SyncState.SYNCING) box.label("Waiting to sync. Your pickup is not reserved until the server accepts this order. Keep this account signed in; sync resumes when you are online.", 14f)
        if (order.syncState == SyncState.FAILED) box.label("This time filled up or expired before your order synced. Cancel this order, then order again with another pickup time.", 15f)
        if (order.status != OrderStatus.CANCELLED) {
            box.label("Your order’s journey", 20f, true)
            listOf(OrderStatus.QUEUED, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED).forEach {
                val reached = it.ordinal <= order.status.ordinal
                box.label("${if (reached) "●" else "○"}  ${it.name.lowercase().replaceFirstChar { c -> c.titlecase() }}", 16f, it == order.status)
            }
        }
        box.label("On your tray", 20f, true)
        vm.items(order).forEach { item -> box.label("${item.quantity} × ${item.name}   ${money(item.quantity * item.unitPrice)}${item.note?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()}", 15f) }
        box.label("Total  ${money(order.totalAmount)}", 24f, true)
        box.label("Pay at pickup · No online payment taken", 13f)
        box.button("Order again") { vm.action("Added to your cart at current prices.") { vm.reorder(order) } }
        if (order.status == OrderStatus.QUEUED) box.button("Cancel order") {
            dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Cancel this order?").setMessage("A live cancellation takes effect after the server accepts it. Orders already confirmed by the stall cannot be cancelled.").setNegativeButton("Keep order", null).setPositiveButton("Cancel order") { _, _ -> vm.submit { vm.cancel(id) } }.show()
        }
        if (vm.isDemo && order.status !in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED)) box.button("Demo: advance order status") { vm.action { vm.advanceDemo(id) } }
        if (order.status == OrderStatus.CANCELLED && order.syncState == SyncState.SYNCED) box.button("Remove from history") { vm.submit { vm.deleteCancelled(order); if (isAdded) findNavController().popBackStack() } }
    }
    private fun editReview() {
        val existing = reviews.find { it.userId == vm.uid.value }
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(24), context.dp(10), context.dp(24), context.dp(10)) }
        val rating = RatingBar(requireContext()).apply { numStars = 5; stepSize = 1f; this.rating = (existing?.rating ?: 5).toFloat() }
        box.addView(rating, LinearLayout.LayoutParams(-2, -2))
        val comment = box.field("How was your meal?", existing?.comment.orEmpty(), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val reviewDialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Your review").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save review", null).create()
        reviewDialog.setOnShowListener { reviewDialog.getButton(-1).setOnClickListener { vm.submit("Review saved.") { vm.review(id, rating.rating.toInt(), comment.value); reviewDialog.dismiss() } } }
        dialog = reviewDialog; reviewDialog.show()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("quantity", quantity); outState.putString("note", note); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { dialog?.dismiss(); dialog = null; listener?.remove(); renderedKey = null; _binding = null; super.onDestroyView() }
}

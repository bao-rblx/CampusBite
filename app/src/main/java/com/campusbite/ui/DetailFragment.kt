package com.campusbite.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
import com.campusbite.viewmodel.CampusState
import com.campusbite.viewmodel.CampusViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class DetailFragment : Fragment() {
    private var _binding: FragmentFormBinding? = null
    private val binding get() = _binding
    private val vm: CampusViewModel by activityViewModels()
    private val itemId get() = requireArguments().getString("id").orEmpty()
    private val kind get() = requireArguments().getString("kind") ?: "dish"
    private var state = CampusState()
    private var reviews: List<ReviewEntity> = emptyList()
    private var quantity = 1
    private var note = ""
    private var listener: ListenerRegistration? = null
    private var dialog: AlertDialog? = null
    private var renderedKey: Any? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        quantity = savedInstanceState?.getInt("quantity", 1) ?: 1; note = savedInstanceState?.getString("note").orEmpty()
        return FragmentFormBinding.inflate(inflater, container, false).also { _binding = it }.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.state.collect { state = it; render() } }
            if (kind == "dish") launch { vm.reviewsFor(itemId).collect { reviews = it; render() } }
        } }
    }
    override fun onStart() { super.onStart(); if (kind == "dish") listener = vm.watchReviews(itemId) }
    override fun onStop() { listener?.remove(); listener = null; super.onStop() }
    private fun render() {
        val box = binding?.content ?: return
        val key = if (kind == "dish") listOf(state.dishes.find { it.dishId == itemId }, reviews, state.user?.uid)
            else listOf(state.orders.find { it.orderId == itemId }, state.stalls)
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
        val dish = state.dishes.find { it.dishId == itemId } ?: return
        val photoCard = MaterialCardView(requireContext()).apply {
            radius = requireContext().dp(24).toFloat()
            cardElevation = requireContext().dp(4).toFloat()
            strokeWidth = requireContext().dp(1)
            setStrokeColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant))
        }
        val photo = ImageView(requireContext()).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = dish.name }
        photoCard.addView(photo, ViewGroup.LayoutParams(-1, requireContext().dp(240)))
        ImageLoader.load(photo, dish.imageUrl, dish.category)
        box.addView(photoCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = requireContext().dp(6); bottomMargin = requireContext().dp(10) })

        box.card {
            val stallName = state.stalls.find { it.stallId == dish.stallId }?.name.orEmpty()
            label("${dish.category.uppercase()}  ·  $stallName", 12f, true)
            label(dish.name, 26f, true)
            val rating = if (reviews.isEmpty()) dish.avgRating.toDouble() else reviews.map { it.rating }.average()
            label("${money(dish.price)}  ·  ★ ${"%.1f".format(rating)}", 20f, true)
        }

        box.card {
            label("Ingredients & allergens", 16f, true)
            label(dish.description, 14f)
        }

        box.button(if (dish.isFavourite) "♥ Saved to favourites" else "♡ Save to favourites") { vm.action { vm.favourite(dish) } }

        box.card {
            label("How many?", 15f, true)
            val stepper = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val minus = com.google.android.material.button.MaterialButton(requireContext()).apply {
                text = "−"; minWidth = context.dp(56); isAllCaps = false
            }
            val count = TextView(requireContext()).apply {
                text = quantity.toString(); textSize = 20f
                gravity = android.view.Gravity.CENTER
            }
            val plus = com.google.android.material.button.MaterialButton(requireContext()).apply {
                text = "+"; minWidth = context.dp(56); isAllCaps = false
            }
            stepper.addView(minus, LinearLayout.LayoutParams(context.dp(56), context.dp(48)))
            stepper.addView(count, LinearLayout.LayoutParams(0, context.dp(48), 1f))
            stepper.addView(plus, LinearLayout.LayoutParams(context.dp(56), context.dp(48)))
            minus.setOnClickListener {
                quantity = (quantity - 1).coerceAtLeast(1); count.text = quantity.toString()
            }
            plus.setOnClickListener {
                quantity = (quantity + 1).coerceAtMost(20); count.text = quantity.toString()
            }
            addView(stepper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(4) })
            field("Note for the stall (optional)", note).edit.doAfterTextChanged { note = it.toString().take(200) }
        }

        box.button(if (dish.isAvailable) "Add to cart" else "Sold out") { vm.action("Added to your cart.") { vm.add(itemId, quantity, note) } }.isEnabled = dish.isAvailable
        box.button("Pre-order →") { vm.action { vm.add(itemId, quantity, note); if (isAdded) (activity as? MainActivity)?.selectTab(R.id.cartFragment) } }.isEnabled = dish.isAvailable

        box.card {
            label("From the campus community", 20f, true)
            button(if (reviews.any { it.userId == vm.uid.value }) "Edit your review" else "Write a review") { editReview() }
            if (reviews.isEmpty()) label("Be the first to share a bite-sized review.", 14f)
            reviews.forEach { review ->
                label("${"★".repeat(review.rating)}  ${review.authorName}", 16f, true)
                label(review.comment.ifBlank { "Rated ${review.rating} out of 5" }, 14f)
                if (review.userId == vm.uid.value) button("Delete your review") {
                    dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Delete this review?").setNegativeButton("Keep", null).setPositiveButton("Delete") { _, _ -> vm.action("Review deleted.") { vm.review(itemId, review.rating, review.comment, true) } }.show()
                }
            }
        }
    }
    private fun renderOrder(box: LinearLayout) {
        val order = state.orders.find { it.orderId == itemId }
        if (order == null) { box.label("Loading your order…", 24f, true); return }

        val containerColor = when (order.status) {
            OrderStatus.READY -> MaterialColors.getColor(box, com.google.android.material.R.attr.colorPrimaryContainer)
            OrderStatus.CANCELLED -> MaterialColors.getColor(box, com.google.android.material.R.attr.colorErrorContainer)
            else -> MaterialColors.getColor(box, com.google.android.material.R.attr.colorSecondaryContainer)
        }

        box.card(bgColor = containerColor) {
            label("ORDER #${itemId.take(8)}", 12f, true)
            label(when { order.syncState == SyncState.FAILED -> "Pickup unavailable"; order.status == OrderStatus.READY -> "Your food is ready!"; order.status == OrderStatus.CANCELLED -> "Order cancelled"; else -> "A good lunch is on the way." }, 26f, true)
            label(state.stalls.find { it.stallId == order.stallId }?.let { "${it.name}\n${it.location}" }.orEmpty(), 16f, true)
            label("Pickup: ${order.pickupWindow}", 15f)
        }

        if (order.syncState == SyncState.PENDING || order.syncState == SyncState.SYNCING) box.card { label("Waiting to sync. Your pickup is not reserved until the server accepts this order. Keep this account signed in; sync resumes when you are online.", 13f) }
        if (order.syncState == SyncState.FAILED) box.card { label("This time filled up or expired before your order synced. Cancel this order, then order again with another pickup time.", 14f) }

        if (order.status != OrderStatus.CANCELLED) {
            box.card {
                label("Your order’s journey", 18f, true)
                listOf(OrderStatus.QUEUED, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED).forEach {
                    val reached = it.ordinal <= order.status.ordinal
                    label("${if (reached) "●" else "○"}  ${it.name.lowercase().replaceFirstChar { c -> c.titlecase() }}", 15f, it == order.status)
                }
            }
        }

        box.card {
            label("On your tray", 18f, true)
            vm.items(order).forEach { item -> label("${item.quantity} × ${item.name}   ${money(item.quantity * item.unitPrice)}${item.note?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()}", 14f) }
            label("Total  ${money(order.totalAmount)}", 22f, true)
            label("Pay at pickup · No online payment taken", 12f)
        }

        box.button("Order again") { vm.action("Added to your cart at current prices.") { vm.reorder(order) } }
        if (order.status == OrderStatus.QUEUED) box.button("Cancel order") {
            dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Cancel this order?").setMessage("A live cancellation takes effect after the server accepts it. Orders already confirmed by the stall cannot be cancelled.").setNegativeButton("Keep order", null).setPositiveButton("Cancel order") { _, _ -> vm.submit { vm.cancel(itemId) } }.show()
        }
        if (vm.isDemo && order.status !in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED)) box.button("Demo: advance order status") { vm.action { vm.advanceDemo(itemId) } }
        if (order.status == OrderStatus.CANCELLED && order.syncState == SyncState.SYNCED) box.button("Remove from history") { vm.submit { vm.deleteCancelled(order); if (isAdded) findNavController().popBackStack() } }
    }
    private fun editReview() {
        val existing = reviews.find { it.userId == vm.uid.value }
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(24), context.dp(10), context.dp(24), context.dp(10)) }
        val rating = RatingBar(requireContext()).apply { numStars = 5; stepSize = 1f; this.rating = (existing?.rating ?: 5).toFloat() }
        box.addView(rating, LinearLayout.LayoutParams(-2, -2))
        val comment = box.field("How was your meal?", existing?.comment.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val reviewDialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Your review").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save review", null).create()
        reviewDialog.setOnShowListener { reviewDialog.getButton(-1).setOnClickListener { vm.submit("Review saved.") { vm.review(itemId, rating.rating.toInt(), comment.value); reviewDialog.dismiss() } } }
        dialog = reviewDialog; reviewDialog.show()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("quantity", quantity); outState.putString("note", note); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { dialog?.dismiss(); dialog = null; listener?.remove(); renderedKey = null; _binding = null; super.onDestroyView() }
}

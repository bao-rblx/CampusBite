package com.campusbite.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.campusbite.data.local.entity.DishEntity
import com.campusbite.databinding.ItemDishBinding
import com.campusbite.databinding.ItemPanelBinding
import com.campusbite.domain.money
import com.google.android.material.chip.Chip

data class RowAction(val label: String, val key: String)
data class DisplayRow(val id: String, val title: String = "", val detail: String = "", val tag: String = "",
    val actions: List<RowAction> = emptyList(), val dish: DishEntity? = null, val stall: String = "", val hero: Boolean = false)

class CampusAdapter(private val action: (String, String) -> Unit) : ListAdapter<DisplayRow, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<DisplayRow>() {
    override fun areItemsTheSame(a: DisplayRow, b: DisplayRow) = a.id == b.id
    override fun areContentsTheSame(a: DisplayRow, b: DisplayRow) = a == b
}) {
    class DishHolder(val binding: ItemDishBinding) : RecyclerView.ViewHolder(binding.root)
    class PanelHolder(val binding: ItemPanelBinding) : RecyclerView.ViewHolder(binding.root)
    override fun getItemViewType(position: Int) = if (getItem(position).dish != null) 1 else 0
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) DishHolder(ItemDishBinding.inflate(inflater, parent, false)) else PanelHolder(ItemPanelBinding.inflate(inflater, parent, false))
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = getItem(position)
        when (holder) {
            is DishHolder -> with(holder.binding) {
                val dish = requireNotNull(row.dish)
                name.text = dish.name; meta.text = "${row.stall} · ★ ${dish.avgRating}"; price.text = money(dish.price)
                Glide.with(photo).load(dish.imageUrl.ifBlank { null }).placeholder(foodArt(dish.category)).error(foodArt(dish.category)).into(photo)
                heart.text = if (dish.isFavourite) "♥" else "♡"
                heart.contentDescription = if (dish.isFavourite) "Remove ${dish.name} from favourites" else "Save ${dish.name} to favourites"
                heart.setOnClickListener { action("favourite", dish.dishId) }
                add.text = if (dish.isAvailable) "+ Add" else "Sold out"; add.isEnabled = dish.isAvailable
                add.contentDescription = "Add ${dish.name} to cart"
                add.setOnClickListener { action("add", dish.dishId) }
                root.setOnClickListener { action("dish", dish.dishId) }
            }
            is PanelHolder -> with(holder.binding) {
                tag.text = row.tag; tag.show(row.tag.isNotBlank()); title.text = row.title; detail.text = row.detail
                val surface = com.google.android.material.color.MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface)
                root.setCardBackgroundColor(if (row.hero) com.google.android.material.color.MaterialColors.getColor(root, com.google.android.material.R.attr.colorSecondaryContainer) else surface)
                actions.removeAllViews()
                row.actions.forEach { a -> actions.addView(Chip(root.context).apply {
                    text = a.label; minHeight = context.dp(48); isCheckable = false; setEnsureMinTouchTargetSize(true)
                    setOnClickListener { action(a.key, row.id) }
                }) }
                actions.show(row.actions.isNotEmpty())
            }
        }
    }
}

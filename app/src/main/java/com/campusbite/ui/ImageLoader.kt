package com.campusbite.ui

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

/**
 * Single Glide entry point for remote dish photography.
 *
 * Performance notes (Milestone 05):
 * - thumbnail(0.25f): loads a low-res version first → perceived load is instant.
 * - DiskCacheStrategy.AUTOMATIC: caches decoded bitmaps on disk and in memory.
 * - centerCrop(): avoids layout re-measurement for consistent card heights.
 * - Placeholder / error fallback: no empty frames during network delay or failures.
 */
object ImageLoader {
    fun load(view: ImageView, url: String?, category: String) {
        Glide.with(view)
            .load(url?.takeIf { it.isNotBlank() })
            .thumbnail(0.25f)                        // show 25% quality preview while full image loads
            .placeholder(foodArt(category))
            .error(foodArt(category))
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
            .into(view)
    }
}

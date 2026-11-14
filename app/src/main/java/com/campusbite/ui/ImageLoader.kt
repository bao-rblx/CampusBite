package com.campusbite.ui

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

/**
 * Single Glide entry point for remote dish photography.
 *
 * Uses a vector placeholder while the request is in flight, the same art as an
 * error fallback if the URL is blank or fails, centre-crop scaling, and
 * DiskCacheStrategy.AUTOMATIC so images are cached in both memory and on disk.
 */
object ImageLoader {
    fun load(view: ImageView, url: String?, category: String) {
        Glide.with(view)
            .load(url?.takeIf { it.isNotBlank() })
            .placeholder(foodArt(category))
            .error(foodArt(category))
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
            .into(view)
    }
}

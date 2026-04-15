package com.trainingvalidator.poc.ui.utils

import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import coil.load
import com.trainingvalidator.poc.R

/**
 * Loads the signed-in user's profile photo into an [ImageView], or shows the default icon with tint.
 * Clears image tint when a remote URL loads successfully so photos are not recolored.
 */
fun ImageView.bindUserAvatar(avatarUrl: String?) {
    val url = avatarUrl?.trim().takeUnless { it.isNullOrEmpty() }
    ImageViewCompat.setImageTintList(this, null)
    if (url == null) {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(R.drawable.ic_person)
        ImageViewCompat.setImageTintList(
            this,
            ContextCompat.getColorStateList(context, R.color.text_secondary)
        )
        return
    }
    scaleType = ImageView.ScaleType.CENTER_CROP
    load(url) {
        placeholder(R.drawable.ic_person)
        error(R.drawable.ic_person)
        crossfade(true)
        listener(
            onSuccess = { _, _ ->
                this@bindUserAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                ImageViewCompat.setImageTintList(this@bindUserAvatar, null)
            },
            onError = { _, _ ->
                this@bindUserAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageResource(R.drawable.ic_person)
                ImageViewCompat.setImageTintList(
                    this@bindUserAvatar,
                    ContextCompat.getColorStateList(context, R.color.text_secondary)
                )
            }
        )
    }
}

package com.trainingvalidator.poc.ui.components.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.trainingvalidator.poc.databinding.ComponentAppHeaderBinding

/**
 * AppHeader - A reusable top navigation bar with back button and title.
 */
class AppHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ComponentAppHeaderBinding

    init {
        binding = ComponentAppHeaderBinding.inflate(LayoutInflater.from(context), this, true)
        
        // Optional title can be set later via setTitle()
    }

    fun setTitle(title: String) {
        binding.headerTitle.text = title
    }

    fun setOnBackClickListener(listener: OnClickListener) {
        binding.backButton.setOnClickListener(listener)
    }

    fun setBackButtonVisibility(visible: Boolean) {
        binding.backButton.visibility = if (visible) View.VISIBLE else View.GONE
    }
}

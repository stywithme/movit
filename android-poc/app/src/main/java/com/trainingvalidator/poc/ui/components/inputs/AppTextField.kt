package com.trainingvalidator.poc.ui.components.inputs

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.google.android.material.textfield.TextInputLayout
import com.trainingvalidator.poc.databinding.ComponentAppTextFieldBinding

/**
 * AppTextField - A custom wrapper for TextInputLayout and TextInputEditText
 * providing a consistent design and easier API.
 */
class AppTextField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ComponentAppTextFieldBinding

    init {
        binding = ComponentAppTextFieldBinding.inflate(LayoutInflater.from(context), this)
        
        // Load attributes
        context.obtainStyledAttributes(attrs, com.google.android.material.R.styleable.TextInputLayout).apply {
            val hint = getString(com.google.android.material.R.styleable.TextInputLayout_android_hint)
            setHint(hint)
            recycle()
        }
    }

    fun setHint(hint: String?) {
        binding.textInputLayout.hint = hint
    }

    fun getText(): String = binding.editText.text.toString()
    
    fun setText(text: String) {
        binding.editText.setText(text)
    }
    
    val textInputLayout: TextInputLayout get() = binding.textInputLayout
}

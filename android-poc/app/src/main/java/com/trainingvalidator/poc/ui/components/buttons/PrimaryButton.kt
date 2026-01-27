package com.trainingvalidator.poc.ui.components.buttons

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import com.trainingvalidator.poc.R

/**
 * PrimaryButton - Styled neon green button for main actions
 */
class PrimaryButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    init {
        // Apply primary style if not specified
        // This is primarily to ensure consistency across the app
    }
}

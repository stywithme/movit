package com.trainingvalidator.poc.ui.components.cards

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView
import com.trainingvalidator.poc.R

/**
 * BaseCard - Standard styled card for the app
 */
class BaseCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {
    
    init {
        // Set standard style properties
    }
}

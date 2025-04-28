package com.example.appblocker.calculator

import androidx.annotation.LayoutRes

/**
 * Represents a page in the calculator onboarding flow.
 * Each page has a layout resource ID and a meaningful identifier.
 *
 * @param layoutResId The layout resource ID for this page
 * @param identifier A semantic identifier for this page (more meaningful than just a number)
 */
data class CalculatorPage(
    @LayoutRes val layoutResId: Int,
    val identifier: String
) 
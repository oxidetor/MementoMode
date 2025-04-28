package com.example.appblocker.views

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.appblocker.R

class AnimatedDotIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var dots: ArrayList<ImageView> = ArrayList()
    private var currentPosition = 0
    private var dotSize = resources.getDimensionPixelSize(R.dimen.dot_size)
    private var dotSpacing = resources.getDimensionPixelSize(R.dimen.dot_spacing)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun setDotCount(count: Int) {
        removeAllViews()
        dots.clear()

        for (i in 0 until count) {
            val dot = ImageView(context).apply {
                setImageResource(R.drawable.dot_indicator_selector)
                layoutParams = LayoutParams(dotSize, dotSize).apply {
                    setMargins(dotSpacing / 2, 0, dotSpacing / 2, 0)
                }
                isSelected = i == currentPosition
            }
            dots.add(dot)
            addView(dot)
        }
    }

    fun setCurrentPosition(position: Int, animate: Boolean = true) {
        if (position == currentPosition || position < 0 || position >= dots.size) return

        val previousDot = dots[currentPosition]
        val newDot = dots[position]

        if (animate) {
            // Animate previous dot
            previousDot.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .alpha(0.5f)
                .setDuration(200)
                .withEndAction {
                    previousDot.isSelected = false
                    previousDot.scaleX = 1f
                    previousDot.scaleY = 1f
                    previousDot.alpha = 1f
                }
                .start()

            // Animate new dot
            newDot.apply {
                scaleX = 0.8f
                scaleY = 0.8f
                alpha = 0.5f
                isSelected = true
            }
            newDot.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(200)
                .start()
        } else {
            previousDot.isSelected = false
            newDot.isSelected = true
        }

        currentPosition = position
    }
} 
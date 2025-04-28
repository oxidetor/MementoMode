package com.example.appblocker

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.appblocker.databinding.ActivitySplashBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAnimations()
        setupClickListeners()
    }

    private fun setupAnimations() {
        val duration = 1000L
        val delay = 300L

        // Logo animation
        val logoScale = ObjectAnimator.ofFloat(binding.logoImage, View.SCALE_X, 0f, 1f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
        }
        val logoScaleY = ObjectAnimator.ofFloat(binding.logoImage, View.SCALE_Y, 0f, 1f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
        }
        val logoAlpha = ObjectAnimator.ofFloat(binding.logoImage, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
        }

        // Title and subtitle animations
        val titleAlpha = ObjectAnimator.ofFloat(binding.appTitle, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
            startDelay = delay
        }
        val subtitleAlpha = ObjectAnimator.ofFloat(binding.appSubtitle, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
            startDelay = delay * 2
        }

        // Button container animation
        val buttonContainerAlpha = ObjectAnimator.ofFloat(binding.buttonContainer, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
            startDelay = delay * 3
        }
        val buttonContainerTranslateY = ObjectAnimator.ofFloat(binding.buttonContainer, View.TRANSLATION_Y, 100f, 0f).apply {
            this.duration = duration
            startDelay = delay * 3
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Play animations together
        AnimatorSet().apply {
            playTogether(
                logoScale, logoScaleY, logoAlpha,
                titleAlpha, subtitleAlpha,
                buttonContainerAlpha, buttonContainerTranslateY
            )
            start()
        }
    }

    private fun setupClickListeners() {
        binding.buttonLogin.setOnClickListener {
            animateButtonClick(it as MaterialButton) {
                showComingSoonDialog("Login functionality will be available in a future update.")
            }
        }

        binding.buttonRegister.setOnClickListener {
            animateButtonClick(it as MaterialButton) {
                showComingSoonDialog("Registration will be available in a future update.")
            }
        }

        binding.buttonContinue.setOnClickListener {
            animateButtonClick(it as MaterialButton) {
                startActivity(Intent(this, ScreentimeCalculatorActivity::class.java))
                finish()
            }
        }
    }

    private fun showComingSoonDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Coming Soon")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun animateButtonClick(button: MaterialButton, onClick: () -> Unit) {
        button.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction {
                        onClick()
                    }
                    .start()
            }
            .start()
    }
} 
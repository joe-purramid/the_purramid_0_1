package com.example.purramid.thepurramid.probabilities.animation

import android.animation.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.random.Random

class DiceAnimationHelper {

    companion object {
        private const val ROLL_DURATION = 1000L // 1 second per spec 14.2.3
        private const val ROLL_DURATION_SHORT = 500L // 0.5 seconds for manual distribution per spec 14.5.10.1.1.1
        private const val ROTATION_COUNT = 3

        fun animateDiceRoll(dieView: View, onAnimationEnd: () -> Unit) {
            animateRoll(dieView, ROLL_DURATION, onAnimationEnd)
        }

        fun animateDiceRollShort(dieView: View, onAnimationEnd: () -> Unit) {
            animateRoll(dieView, ROLL_DURATION_SHORT, onAnimationEnd)
        }

        private fun animateRoll(dieView: View, duration: Long, onAnimationEnd: () -> Unit) {
            val animatorSet = AnimatorSet()

            // Rotation animations
            val rotationX = ObjectAnimator.ofFloat(dieView, View.ROTATION_X, 0f, 360f * ROTATION_COUNT)
            val rotationY = ObjectAnimator.ofFloat(dieView, View.ROTATION_Y, 0f, 360f * ROTATION_COUNT)

            // Slight bounce
            val scaleX = ObjectAnimator.ofFloat(dieView, View.SCALE_X, 1f, 1.2f, 1f)
            val scaleY = ObjectAnimator.ofFloat(dieView, View.SCALE_Y, 1f, 1.2f, 1f)

            // Random slight movement
            val translateX = ObjectAnimator.ofFloat(
                dieView,
                View.TRANSLATION_X,
                0f,
                Random.nextFloat() * 20f - 10f,
                0f
            )
            val translateY = ObjectAnimator.ofFloat(
                dieView,
                View.TRANSLATION_Y,
                0f,
                Random.nextFloat() * 20f - 10f,
                0f
            )

            animatorSet.playTogether(rotationX, rotationY, scaleX, scaleY, translateX, translateY)
            animatorSet.duration = duration
            animatorSet.interpolator = DecelerateInterpolator()

            animatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Reset view properties
                    dieView.rotationX = 0f
                    dieView.rotationY = 0f
                    onAnimationEnd()
                }
            })

            animatorSet.start()
        }

        fun animateCriticalHit(dieView: View) {
            val pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                dieView,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.3f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.3f, 1f)
            )
            pulseAnimator.duration = 300
            pulseAnimator.repeatCount = 2
            pulseAnimator.start()
        }
    }
}
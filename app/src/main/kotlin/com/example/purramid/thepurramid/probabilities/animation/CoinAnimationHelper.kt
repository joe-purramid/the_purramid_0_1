package com.example.purramid.thepurramid.probabilities.animation

import android.animation.*
import android.widget.ImageView
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinType
import com.example.purramid.thepurramid.R

class CoinAnimationHelper {

    companion object {
        private const val FLIP_DURATION = 1000L // 1 second per spec 15.2.3

        fun animateCoinFlip(coinView: ImageView, isHeads: Boolean, onAnimationEnd: () -> Unit) {
            val coinType = coinView.getTag(R.id.coin_type) as? CoinType ?: return

            val rotationY = ObjectAnimator.ofFloat(coinView, View.ROTATION_Y, 0f, 1800f) // 5 full rotations
            rotationY.duration = FLIP_DURATION
            rotationY.interpolator = DecelerateInterpolator()

            // Change image at midpoint
            rotationY.addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                if (value >= 900f && value < 910f) { // Roughly midpoint
                    updateCoinImage(coinView, coinType, isHeads)
                }
            }

            rotationY.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    coinView.rotationY = 0f
                    onAnimationEnd()
                }
            })

            rotationY.start()
        }

        fun animateCoinFlipNoChange(coinView: ImageView, onAnimationEnd: () -> Unit) {
            // For when animation is on but result doesn't change
            val wobble = ObjectAnimator.ofFloat(coinView, View.ROTATION, -10f, 10f, -5f, 5f, 0f)
            wobble.duration = 500
            wobble.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onAnimationEnd()
                }
            })
            wobble.start()
        }

        private fun updateCoinImage(coinView: ImageView, coinType: CoinType, isHeads: Boolean) {
            val drawableRes = when (coinType) {
                CoinType.B1 -> if (isHeads) R.drawable.b1_coin_flip_heads else R.drawable.b1_coin_flip_tails
                CoinType.B5 -> if (isHeads) R.drawable.b5_coin_flip_heads else R.drawable.b5_coin_flip_tails
                CoinType.B10 -> if (isHeads) R.drawable.b10_coin_flip_heads else R.drawable.b10_coin_flip_tails
                CoinType.B25 -> if (isHeads) R.drawable.b25_coin_flip_heads else R.drawable.b25_coin_flip_tails
                CoinType.MB1 -> if (isHeads) R.drawable.mb1_coin_flip_heads else R.drawable.mb1_coin_flip_tails
                CoinType.MB2 -> if (isHeads) R.drawable.mb2_coin_flip_heads else R.drawable.mb2_coin_flip_tails
            }
            coinView.setImageResource(drawableRes)
        }
    }
}
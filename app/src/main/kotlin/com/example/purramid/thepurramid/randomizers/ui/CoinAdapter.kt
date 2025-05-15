// CoinAdapter.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ItemCoinBinding
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinFace
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinFlipViewModel // For FLIP_ANIMATION_DURATION_MS
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinInPool
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinType

class CoinAdapter(
    private var coinColor: Int,
    private var animateFlips: Boolean,
    private val getCoinDrawableResFunction: (CoinType, CoinFace) -> Int
) : ListAdapter<CoinInPool, CoinAdapter.CoinViewHolder>(CoinDiffCallback()) {

    // To keep track of coins that are currently undergoing the flip animation initiated by the adapter
    private val flippingCoins = mutableSetOf<UUID>()

    fun updateCoinAppearanceProperties(newCoinColor: Int, newAnimateFlips: Boolean) {
        val colorChanged = coinColor != newCoinColor
        val animationPrefChanged = animateFlips != newAnimateFlips
        coinColor = newCoinColor
        animateFlips = newAnimateFlips
        if (colorChanged) {
            notifyDataSetChanged() // Rebind all visible items to update color
        }
        // No need to notify for animationPrefChanged unless it immediately affects static appearance
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoinViewHolder {
        val binding = ItemCoinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CoinViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CoinViewHolder, position: Int) {
        val coin = getItem(position)
        holder.bind(coin, coinColor, getCoinDrawableResFunction)

        // If this coin was just flipped and animation is enabled, and it's not already marked as flipping by adapter
        if (coin.currentFace != CoinFace.HEADS && animateFlips && !flippingCoins.contains(coin.id)) {
           // This simplified animation trigger might need refinement.
           // The ViewModel should ideally tell the adapter *which specific items* just got new results.
           // For now, if a coin is Tails and wasn't previously, we might animate it if it's visible.
        }
    }

    // Call this method from the Fragment when a flip happens for specific items
    fun triggerFlipAnimationForItem(coinId: UUID, finalFace: CoinFace) {
        val position = currentList.indexOfFirst { it.id == coinId }
        if (position != -1 && animateFlips) {
            flippingCoins.add(coinId)
            notifyItemChanged(position, "FLIP_ANIMATION_PAYLOAD") // Trigger onBindViewHolder with payload
        }
    }


    inner class CoinViewHolder(private val binding: ItemCoinBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            coin: CoinInPool,
            colorInt: Int,
            getDrawableRes: (CoinType, CoinFace) -> Int
        ) {
            val drawableRes = getDrawableRes(coin.type, coin.currentFace)
            binding.coinImageView.setImageResource(drawableRes)

            if (colorInt != Color.TRANSPARENT) {
                binding.coinImageView.colorFilter = PorterDuffColorFilter(colorInt, PorterDuff.Mode.SRC_IN)
            } else {
                binding.coinImageView.clearColorFilter()
            }
            // Ensure view is reset if not animating
            binding.coinImageView.rotationY = 0f
            binding.coinImageView.alpha = 1f
        }

        fun animateFlip(coin: CoinInPool, finalDrawableRes: Int, onEnd: () -> Unit) {
            binding.coinImageView.rotationY = 0f // Start from a flat state
            val animator = ObjectAnimator.ofFloat(binding.coinImageView, View.ROTATION_Y, 0f, 180f * 3).apply {
                duration = CoinFlipViewModel.FLIP_ANIMATION_DURATION_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val animatedValue = valueAnimator.animatedValue as Float
                    // Change drawable mid-animation (e.g., when coin is edge-on)
                    // This is a simplified way; true 3D would involve more complex views or RenderScript/OpenGL
                    if (animatedValue >= 90f && binding.coinImageView.tag != finalDrawableRes) {
                        binding.coinImageView.setImageResource(finalDrawableRes)
                        binding.coinImageView.tag = finalDrawableRes // Prevent multiple sets
                    }
                }
                doOnEnd {
                    binding.coinImageView.rotationY = 0f // Ensure it ends flat
                    binding.coinImageView.setImageResource(finalDrawableRes) // Ensure final state
                    binding.coinImageView.tag = null // Clear tag
                    onEnd()
                }
            }
            animator.start()
        }
    }

    // DiffUtil for efficient updates
    class CoinDiffCallback : DiffUtil.ItemCallback<CoinInPool>() {
        override fun areItemsTheSame(oldItem: CoinInPool, newItem: CoinInPool): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CoinInPool, newItem: CoinInPool): Boolean {
            return oldItem == newItem // Checks type, currentFace, xPos, yPos
        }
    }

     override fun onBindViewHolder(holder: CoinViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("FLIP_ANIMATION_PAYLOAD")) {
            val coin = getItem(position)
            if (animateFlips) {
                holder.animateFlip(coin, getCoinDrawableResFunction(coin.type, coin.currentFace)) {
                    flippingCoins.remove(coin.id) // Remove from flipping set when animation ends
                }
            } else {
                // If animation got turned off mid-flip or for some reason, just bind normally
                holder.bind(coin, coinColor, getCoinDrawableResFunction)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}
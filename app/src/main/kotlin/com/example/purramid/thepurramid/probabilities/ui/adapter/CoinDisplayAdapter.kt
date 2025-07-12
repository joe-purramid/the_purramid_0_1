// CoinDisplayAdapter.kt
package com.example.purramid.thepurramid.probabilities.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.probabilities.animation.CoinAnimationHelper
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinConfig
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinType

class CoinDisplayAdapter(
    private val coinConfigs: List<CoinConfig>
) : RecyclerView.Adapter<CoinDisplayAdapter.CoinViewHolder>() {
    
    private var results: Map<CoinType, List<Boolean>> = emptyMap()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoinViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_coin, parent, false)
        return CoinViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CoinViewHolder, position: Int) {
        val (coinType, coinIndex) = getTypeAndIndexForPosition(position)
        val config = coinConfigs.find { it.type == coinType }
        val isHeads = results[coinType]?.getOrNull(coinIndex) ?: true
        
        holder.bind(coinType, config?.color ?: 0xFFDAA520.toInt(), isHeads)
    }
    
    override fun getItemCount(): Int {
        return coinConfigs.sumOf { it.quantity }
    }
    
    fun updateResults(newResults: Map<CoinType, List<Boolean>>) {
        results = newResults
        notifyDataSetChanged()
    }
    
    fun animateFlips(newResults: Map<CoinType, List<Boolean>>) {
        results = newResults
        // Animation handled in ViewHolder
        notifyDataSetChanged()
    }
    
    private fun getTypeAndIndexForPosition(position: Int): Pair<CoinType, Int> {
        var currentPosition = 0
        coinConfigs.forEach { config ->
            if (position < currentPosition + config.quantity) {
                return Pair(config.type, position - currentPosition)
            }
            currentPosition += config.quantity
        }
        return Pair(CoinType.B25, 0)
    }
    
    inner class CoinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coinImageView: ImageView = itemView.findViewById(R.id.coinImageView)
        
        fun bind(coinType: CoinType, color: Int, isHeads: Boolean) {
            val drawableRes = when (coinType) {
                CoinType.B1 -> if (isHeads) R.drawable.b1_coin_flip_heads else R.drawable.b1_coin_flip_tails
                CoinType.B5 -> if (isHeads) R.drawable.b5_coin_flip_heads else R.drawable.b5_coin_flip_tails
                CoinType.B10 -> if (isHeads) R.drawable.b10_coin_flip_heads else R.drawable.b10_coin_flip_tails
                CoinType.B25 -> if (isHeads) R.drawable.b25_coin_flip_heads else R.drawable.b25_coin_flip_tails
                CoinType.MB1 -> if (isHeads) R.drawable.mb1_coin_flip_heads else R.drawable.mb1_coin_flip_tails
                CoinType.MB2 -> if (isHeads) R.drawable.mb2_coin_flip_heads else R.drawable.mb2_coin_flip_tails
            }
            
            coinImageView.setImageResource(drawableRes)
            coinImageView.setColorFilter(color)
            coinImageView.setTag(R.id.coin_type, coinType)
            coinImageView.setTag(R.id.coin_is_heads, isHeads)
        }
    }
}
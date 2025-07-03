// SequenceListAdapter.kt
package com.example.purramid.thepurramid.traffic_light

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ItemSequenceListBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor
import com.example.purramid.thepurramid.traffic_light.viewmodel.TimedSequence

class SequenceListAdapter(
    private val onSequenceClick: (TimedSequence) -> Unit,
    private val onDeleteClick: (TimedSequence) -> Unit
) : ListAdapter<TimedSequence, SequenceListAdapter.SequenceViewHolder>(SequenceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SequenceViewHolder {
        val binding = ItemSequenceListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SequenceViewHolder(binding)
    }

    inner class SequenceViewHolder(
        private val binding: ItemSequenceListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onSequenceClick(getItem(position))
                }
            }

            binding.buttonDeleteSequence.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }
        }

        fun bind(sequence: TimedSequence) {
            binding.textSequenceTitle.text = sequence.title
            binding.textTotalDuration.text = "Total: ${sequence.getTotalDurationFormatted()}"
            binding.textStepCount.text = "${sequence.steps.size} steps"

            // Clear and add color preview circles
            binding.layoutColorPreview.removeAllViews()
            sequence.steps.forEach { step ->
                val colorView = ImageView(binding.root.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        binding.root.context.resources.getDimensionPixelSize(R.dimen.small_color_indicator_size),
                        binding.root.context.resources.getDimensionPixelSize(R.dimen.small_color_indicator_size)
                    ).apply {
                        marginEnd = binding.root.context.resources.getDimensionPixelSize(R.dimen.small_spacing)
                    }
                    setImageResource(R.drawable.ic_circle_base)
                    
                    val tintColor = when (step.color) {
                        LightColor.RED -> 0xFFFF0000.toInt()
                        LightColor.YELLOW -> 0xFFFFFF00.toInt()
                        LightColor.GREEN -> 0xFF00FF00.toInt()
                        null -> 0xFFCCCCCC.toInt() // Gray for unassigned
                    }
                    setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                }
                binding.layoutColorPreview.addView(colorView)
            }
        }
    }

    class SequenceDiffCallback : DiffUtil.ItemCallback<TimedSequence>() {
        override fun areItemsTheSame(oldItem: TimedSequence, newItem: TimedSequence): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TimedSequence, newItem: TimedSequence): Boolean {
            return oldItem == newItem
        }
    }
}
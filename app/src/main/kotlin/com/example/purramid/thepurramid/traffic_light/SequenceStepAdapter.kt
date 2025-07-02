// SequenceStepAdapter.kt
package com.example.purramid.thepurramid.traffic_light

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ItemSequenceStepBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor
import com.example.purramid.thepurramid.traffic_light.viewmodel.SequenceStep

class SequenceStepAdapter(
    private val onColorClick: (Int, SequenceStep) -> Unit,
    private val onDurationClick: (Int, SequenceStep) -> Unit,
    private val onMessageClick: (Int, SequenceStep) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : ListAdapter<SequenceStep, SequenceStepAdapter.StepViewHolder>(StepDiffCallback()) {

    var dragHandleTouchListener: ((RecyclerView.ViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemSequenceStepBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class StepViewHolder(
        private val binding: ItemSequenceStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.imageDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dragHandleTouchListener?.invoke(this)
                }
                false
            }

            binding.imageColorIndicator.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onColorClick(position, getItem(position))
                }
            }

            binding.textDuration.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDurationClick(position, getItem(position))
                }
            }

            binding.buttonMessage.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMessageClick(position, getItem(position))
                }
            }

            binding.buttonDelete.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(position)
                }
            }
        }

        fun bind(step: SequenceStep, position: Int) {
            binding.textStepNumber.text = (position + 1).toString()
            binding.textDuration.text = step.getDurationFormatted()

            // Set color indicator
            val tintColor = when (step.color) {
                LightColor.RED -> 0xFFFF0000.toInt()
                LightColor.YELLOW -> 0xFFFFFF00.toInt()
                LightColor.GREEN -> 0xFF00FF00.toInt()
                null -> 0xFFCCCCCC.toInt() // Gray for unassigned
            }
            binding.imageColorIndicator.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

            // Show message preview if exists
            val hasMessage = !step.message.isEmpty()
            binding.textMessagePreview.isVisible = hasMessage
            if (hasMessage) {
                val preview = buildString {
                    if (step.message.text.isNotEmpty()) {
                        append(step.message.text)
                    }
                    if (step.message.emojis.isNotEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append(step.message.emojis.joinToString(""))
                    }
                    if (step.message.imageUri != null) {
                        if (isNotEmpty()) append(" ")
                        append("📷")
                    }
                }
                binding.textMessagePreview.text = preview
            }

            // Update message button appearance
            binding.buttonMessage.alpha = if (hasMessage) 1.0f else 0.5f
        }
    }

    class StepDiffCallback : DiffUtil.ItemCallback<SequenceStep>() {
        override fun areItemsTheSame(oldItem: SequenceStep, newItem: SequenceStep): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SequenceStep, newItem: SequenceStep): Boolean {
            return oldItem == newItem
        }
    }
}
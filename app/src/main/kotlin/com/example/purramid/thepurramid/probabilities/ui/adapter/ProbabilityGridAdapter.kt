// ProbabilityGridAdapter.kt
package com.example.purramid.thepurramid.probabilities.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.probabilities.viewmodel.GridCellResult

class ProbabilityGridAdapter(
    private val totalCells: Int,
    private val onCellClick: (Int) -> Unit
) : RecyclerView.Adapter<ProbabilityGridAdapter.GridCellViewHolder>() {
    
    private val results = mutableListOf<GridCellResult>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridCellViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_probability_grid_cell, parent, false)
        return GridCellViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: GridCellViewHolder, position: Int) {
        val result = results.getOrNull(position)
        holder.bind(result, position)
    }
    
    override fun getItemCount(): Int = totalCells
    
    fun updateResults(newResults: List<GridCellResult>) {
        results.clear()
        results.addAll(newResults)
        notifyDataSetChanged()
    }
    
    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }
    
    inner class GridCellViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cellTextView: TextView = itemView.findViewById(R.id.gridCellTextView)
        
        fun bind(result: GridCellResult?, position: Int) {
            if (result != null) {
                val text = when {
                    result.headsCount == 0 -> "${result.tailsCount}T"
                    result.tailsCount == 0 -> "${result.headsCount}H"
                    else -> "${result.headsCount}H/${result.tailsCount}T"
                }
                cellTextView.text = text
                itemView.isClickable = false
            } else {
                cellTextView.text = ""
                itemView.isClickable = true
                itemView.setOnClickListener {
                    onCellClick(position)
                }
            }
        }
    }
}
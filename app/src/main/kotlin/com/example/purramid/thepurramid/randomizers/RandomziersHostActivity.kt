// RandomizersHostActivity.kt
package com.example.purramid.thepurramid.randomizers // Or your chosen package

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.purramid.thepurramid.R // Import base R
import com.example.purramid.thepurramid.databinding.ActivityRandomizersHostBinding // Use generated binding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint // Annotate for Hilt
class RandomizersHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomizersHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomizersHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigation setup is mostly handled by the NavHostFragment and nav graph
        // You might add Toolbar/AppBar setup here later if needed
    }

    // You might override onSupportNavigateUp() later if using an ActionBar
    // override fun onSupportNavigateUp(): Boolean {
    //     val navController = findNavController(R.id.nav_host_fragment_randomizers)
    //     return navController.navigateUp() || super.onSupportNavigateUp()
    // }
}
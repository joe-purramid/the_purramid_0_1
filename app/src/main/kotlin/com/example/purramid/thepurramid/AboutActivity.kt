package com.example.purramid.thepurramid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    val eulaText = getString(R.string.eula_content_part1) +
            getString(R.string.eula_content_part2) +
            getString(R.string.eula_content_part3) +
            getString(R.string.eula_content_part4) +
            getString(R.string.eula_content_part5) +
            getString(R.string.eula_content_part6) +
            getString(R.string.eula_content_part7) +
            getString(R.string.eula_content_part8) +
            getString(R.string.eula_content_part9) +
            getString(R.string.eula_content_part10)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
    }
}
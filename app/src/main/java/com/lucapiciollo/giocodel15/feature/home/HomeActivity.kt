package com.lucapiciollo.giocodel15.feature.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.databinding.ActivityHomeBinding
import com.lucapiciollo.giocodel15.feature.create.CreateTableActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.createTableButton.setOnClickListener {
            startActivity(Intent(this, CreateTableActivity::class.java))
        }

        binding.joinTableButton.setOnClickListener {
            // Nearby table discovery will be wired in the next milestone.
        }
    }
}

package com.lucapiciollo.giocodel15.feature.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.databinding.ActivityHomeBinding
import com.lucapiciollo.giocodel15.feature.create.CreateTableActivity
import com.lucapiciollo.giocodel15.feature.nearby.NearbyTablesActivity
import com.lucapiciollo.giocodel15.feature.solo.SoloSetupActivity

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
            startActivity(Intent(this, NearbyTablesActivity::class.java))
        }

        binding.soloPlayButton.setOnClickListener {
            startActivity(Intent(this, SoloSetupActivity::class.java))
        }
    }
}

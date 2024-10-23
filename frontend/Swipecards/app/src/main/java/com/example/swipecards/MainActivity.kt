package com.example.swipecards

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val images = listOf(
            R.drawable.image1,
            R.drawable.image2,
            R.drawable.image3
        )

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        viewPager.adapter = ImageAdapter(images)

        val btnReject: Button = findViewById(R.id.btnReject)
        val btnAccept: Button = findViewById(R.id.btnAccept)

        btnReject.setOnClickListener {
            Toast.makeText(this, "Rejected", Toast.LENGTH_SHORT).show()
        }

        btnAccept.setOnClickListener {
            Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
        }
    }
}
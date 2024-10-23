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
            R.drawable.aapl,
            R.drawable.ibm,
            R.drawable.nvda,
            R.drawable.tlsa,
            R.drawable.save,
            R.drawable.mara,
            R.drawable.djt,
            R.drawable.bivi,
            R.drawable.plug,
            R.drawable.pltr,
        )

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        viewPager.adapter = ImageAdapter(images)

        viewPager.getChildAt(0).setOnTouchListener { _, _ -> true }

        val btnReject: Button = findViewById(R.id.btnReject)
        val btnAccept: Button = findViewById(R.id.btnAccept)

        btnReject.setOnClickListener {
            Toast.makeText(this, "Rejected", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = viewPager.currentItem + 1
        }

        btnAccept.setOnClickListener {
            Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = viewPager.currentItem + 1
        }
    }
}
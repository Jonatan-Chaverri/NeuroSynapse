package com.example.swipecards

import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

class NoSwipeItemTouchListener : RecyclerView.OnItemTouchListener {
    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        return true // Intercept all touch events
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        // Do nothing
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Do nothing
    }
}
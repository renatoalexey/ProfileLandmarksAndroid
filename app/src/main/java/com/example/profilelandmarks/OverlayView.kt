package com.example.profilelandmarks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val points = mutableListOf<Triple<Float, Float, Int>>() // x, y, índice
    private val paintCircle = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val paintText = Paint().apply {
        color = Color.BLUE
        textSize = 24f
        isAntiAlias = true
    }

    fun setPoints(list: List<Triple<Float, Float, Int>>) {
        points.clear()
        points.addAll(list)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((x, y, index) in points) {
            canvas.drawCircle(x, y, 10f, paintCircle)
            canvas.drawText(index.toString(), x + 15, y, paintText)
        }
    }
}

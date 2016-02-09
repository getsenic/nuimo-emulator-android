/**
 *  Created by Lars Blumberg on 2/9/16.
 *  Copyright © 2015 Senic. All rights reserved.
 *
 *  This software may be modified and distributed under the terms
 *  of the MIT license.  See the LICENSE file for details. *
 */

package com.senic.nuimo.emulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

open class DialView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var ringSize = 40
        set(value) {field = value; invalidate()}

    var handleSize = 50
        set(value) {field = value; invalidate()}

    var value = 0.0f
        set(value) {
            if (field == value) return
            val oldValue = field
            field = value
            invalidate()
            dialListener?.onChangeValue(value, oldValue)
        }

    open var dialListener: DialListener? = null

    private var isDragging = false
    //TODO: Provide color properties as XML layout attributes
    private val ringPaint    = Paint().apply { color = Color.argb(255,  37,  37,  37); flags = Paint.ANTI_ALIAS_FLAG }
    private val surfacePaint = Paint().apply { color = Color.argb(255,  55,  55,  55); flags = Paint.ANTI_ALIAS_FLAG }
    private val handlePaint  = Paint().apply { color = Color.argb(127, 127, 127, 127); flags = Paint.ANTI_ALIAS_FLAG }

    private val size: Int get() = Math.min(width, height)
    private val rotationSize: Int get() = size - Math.max(handleSize, ringSize)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (isEnabled && (Math.abs(Math.sqrt(Math.pow(event.x - width / 2.0, 2.0) + Math.pow(height / 2.0 - event.y, 2.0)) - rotationSize / 2.0) < Math.max(handleSize, ringSize) / 2.0f)) {
                isDragging = true
                dialListener?.onStartDragging()
                performDrag(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE ->  if (isDragging) {
                performDrag(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> if (isDragging) {
                isDragging = false
                dialListener?.onEndDragging()
            }
        }
        return isDragging
    }

    private fun performDrag(x: Float, y: Float) {
        val pos = (Math.atan2(x - width / 2.0, height / 2.0 - y) / 2.0 / Math.PI).toFloat()
        value = pos + if (pos >= 0) 0.0f else 1.0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2.0f
        val centerY = height / 2.0f
        val deltaX = (Math.sin(value * 2.0f * Math.PI) * rotationSize / 2.0f).toFloat()
        val deltaY = (Math.cos(value * 2.0f * Math.PI) * rotationSize / 2.0f).toFloat()

        canvas.apply {
            drawCircle(centerX, centerY, (rotationSize + ringSize) / 2.0f, ringPaint)
            drawCircle(centerX, centerY, (rotationSize - ringSize) / 2.0f, surfacePaint)

            if (!isEnabled) return

            canvas.drawCircle(centerX + deltaX, centerY - deltaY, handleSize / 2.0f, handlePaint)
        }
    }

    interface DialListener {
        fun onChangeValue(value: Float, oldValue: Float)
        fun onStartDragging()
        fun onEndDragging()
    }
}

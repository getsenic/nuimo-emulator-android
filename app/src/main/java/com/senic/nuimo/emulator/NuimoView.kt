/**
 *  Created by Lars Blumberg on 2/8/16.
 *  Copyright © 2015 Senic. All rights reserved.
 *
 *  This software may be modified and distributed under the terms
 *  of the MIT license.  See the LICENSE file for details. *
 */

package com.senic.nuimo.emulator

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import java.util.*
import kotlin.concurrent.timerTask

class NuimoView(context: Context, attrs: AttributeSet?) : DialView(context, attrs), DialView.DialListener {

    var gestureEventListener: GestureEventListener? = null
    var ledAlpha = 1.0f
        set(value) {
            if (value == field) return
            field = value
            ledPaint.alpha = Math.max(0, Math.min(255, (255 * value).toInt()))
            invalidate()
        }

    override var dialListener: DialListener? = this

    private var isFirstDrag = false
    private val gestureDetector = GestureDetector(context, GestureListener())
    private var leds = booleanArrayOf()
        set(value) {field = value; invalidate()}
    private val ledPaint = Paint().apply { color = Color.argb(255,  255,  255,  255); flags = Paint.ANTI_ALIAS_FLAG }
    private val ledAlphaAnimator = ObjectAnimator().apply { target = this@NuimoView; propertyName = "ledAlpha" }
    private var ledFadeOutTimer = Timer()
    private val flySensorPaint = Paint().apply { color = Color.argb(255,  37,  37,  37); flags = Paint.ANTI_ALIAS_FLAG }

    fun displayLedMatrix(leds: BooleanArray, brightness: Float, displayInterval: Float) {
        ledFadeOutTimer.cancel()
        ledFadeOutTimer = Timer()
        this.leds = leds
        ledAlphaAnimator.animate(ledAlpha, brightness, 400)
        if (displayInterval > 0) {
            ledFadeOutTimer.schedule(timerTask { runOnUiThread { ledAlphaAnimator.animate(ledAlpha, 0.0f, 1000) } }, (displayInterval * 1000).toLong())
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val nuimoSize = Math.min(w, h)
        ringSize = (nuimoSize * 0.11f).toInt()
        handleSize = (ringSize * 1.5).toInt()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (super.onTouchEvent(event)) {
            true  -> true /* Dial ring is rotated */
            false -> gestureDetector.onTouchEvent(event)
        }
    }

    override fun onStartDragging() {
        isFirstDrag = true
    }

    override fun onEndDragging() { }

    override fun onChangeValue(value: Float, oldValue: Float) {
        if (isFirstDrag) {
            isFirstDrag = false
            return
        }
        var delta = value - oldValue
        if      (delta >  0.5) delta = 1 - delta
        else if (delta < -0.5) delta = 1 + delta
        gestureEventListener?.onRotate(delta)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = Math.min(width, height)
        val sensorHeight = size * 0.1f
        val sensorWidth = sensorHeight * 0.3f
        val sensorLeft = (width - sensorWidth) / 2.0f
        val sensorTop = height / 2.0f - size * 0.3f
        val sensorCornerRadius = sensorWidth / 2.0f
        canvas.drawRoundRect(sensorLeft, sensorTop, sensorLeft + sensorWidth, sensorTop + sensorHeight, sensorCornerRadius, sensorCornerRadius, flySensorPaint)

        if (!isEnabled) return

        val matrixSize = size * 0.22f
        val matrixLeft = (width - matrixSize) / 2.0f
        val matrixTop = (height - matrixSize) / 2.0f
        val ledSize = matrixSize * 0.09f
        val padding = (matrixSize - 9 * ledSize) / 8

        (0..8).forEach { row ->
            (0..8).forEach { col ->
                if (leds.getOrElse(row * 9 + col, { false })) {
                    canvas.drawCircle(matrixLeft + col * (ledSize + padding) + ledSize / 2.0f, matrixTop + row * (ledSize + padding) + ledSize / 2.0f, ledSize / 2.0f, ledPaint)}}}
    }

    interface GestureEventListener {
        fun onButtonPress()
        fun onButtonRelease()
        fun onSwipe(direction: NuimoSwipeDirection)
        fun onRotate(value: Float)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        private val minSwipeDistance = 100
        private val minSwipeVelocity = 100

        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            gestureEventListener?.onButtonPress()
            gestureEventListener?.onButtonRelease()
            return true
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y
            val swipeDirection: NuimoSwipeDirection? =
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        when {
                            Math.abs(diffX) < minSwipeDistance || Math.abs(velocityX) < minSwipeVelocity -> null
                            diffX > 0 -> NuimoSwipeDirection.RIGHT
                            else      -> NuimoSwipeDirection.LEFT
                        }
                    } else {
                        when {
                            Math.abs(diffY) < minSwipeDistance || Math.abs(velocityY) < minSwipeVelocity -> null
                            diffY > 0 -> NuimoSwipeDirection.DOWN
                            else      -> NuimoSwipeDirection.UP
                        }
                    }
            if (swipeDirection != null) {
                gestureEventListener?.onSwipe(swipeDirection)
            }
            return swipeDirection != null
        }
    }
}

private fun ObjectAnimator.animate(from: Float, to: Float, duration: Long) {
    cancel()
    setFloatValues(from, to)
    this.duration = duration
    start()
}

private fun runOnUiThread(what: () -> (Unit)) {
    Handler(Looper.getMainLooper()).post {
        what()
    }
}

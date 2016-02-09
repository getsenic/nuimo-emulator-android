/**
 *  Created by Lars Blumberg on 2/8/16.
 *  Copyright © 2015 Senic. All rights reserved.
 *
 *  This software may be modified and distributed under the terms
 *  of the MIT license.  See the LICENSE file for details. *
 */

package com.senic.nuimo.emulator

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class NuimoView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var gestureEventListener: GestureEventListener? = null

    private val gestureDetector = GestureDetector(context, GestureListener())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isDragging = false

        return when (isDragging) {
            true  -> true// Handle drag
            false -> gestureDetector.onTouchEvent(event)
        }
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
                            diffX > 0 -> NuimoSwipeDirection.LEFT
                            else      -> NuimoSwipeDirection.RIGHT
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

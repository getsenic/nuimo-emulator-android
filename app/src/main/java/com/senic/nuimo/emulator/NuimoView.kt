/**
 *  Created by Lars Blumberg on 2/5/16.
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

    var gestureDetectorListener: GestureDetector.OnGestureListener? = null
        set(value) {
            field = value
            gestureDetector = if (value == null) { null } else { GestureDetector(context, value) }
        }

    private var gestureDetector: GestureDetector? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(event)
        return true
    }

}

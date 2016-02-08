package com.senic.nuimo.emulator

import android.bluetooth.BluetoothDevice
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import butterknife.bindView

class MainActivity : AppCompatActivity(), NuimoListener, GestureDetector.OnGestureListener {
    val nuimo: Nuimo by lazy{ Nuimo(this).apply{ addListener(this@MainActivity) } }

    val nuimoView: NuimoView by bindView(R.id.nuimo)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nuimoView.gestureDetectorListener = this

        nuimo.powerOn()
    }

    override fun onDestroy() {
        nuimo.powerOff()

        super.onDestroy()
    }

    /*
     * NuimoListener
     */

    override fun onConnect(device: BluetoothDevice) {
        runOnUiThread { Toast.makeText(this, "Connected to ${device.address}", Toast.LENGTH_SHORT).show() }
    }

    override fun onDisconnect(device: BluetoothDevice) {
        runOnUiThread { Toast.makeText(this, "Disconnected from ${device.address}", Toast.LENGTH_SHORT).show() }
    }

    /*
     * OnGestureListener
     */

    override fun onSingleTapUp(p0: MotionEvent?): Boolean {
        nuimo.pressButton()
        nuimo.releaseButton()
        return true
    }

    override fun onLongPress(p0: MotionEvent?) {
    }

    override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        return false
    }

    override fun onDown(p0: MotionEvent?): Boolean {
        return false
    }

    override fun onFling(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        return false
    }

    override fun onShowPress(p0: MotionEvent?) {

    }
}

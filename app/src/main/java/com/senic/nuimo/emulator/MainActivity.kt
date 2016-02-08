package com.senic.nuimo.emulator

import android.bluetooth.BluetoothDevice
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.GestureDetector
import android.widget.Toast
import butterknife.bindView

class MainActivity : AppCompatActivity(), NuimoListener {
    val nuimo: Nuimo by lazy{ Nuimo(this).apply{ addListener(this@MainActivity) } }

    val nuimoView: NuimoView by bindView(R.id.nuimo)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //TODO: Clean code, create a custom GestureDetector like class from OnSwipeTouchListener that takes a listener (here: this@MainActivity)
        nuimoView.setOnTouchListener(object: OnSwipeTouchListener(this@MainActivity) {
            override fun onSingleTapUp() {
                nuimo.pressButton()
                nuimo.releaseButton()
            }
            override fun onSwipeLeft() {
                nuimo.swipe(NuimoSwipeDirection.LEFT)
            }
            override fun onSwipeRight() {
                nuimo.swipe(NuimoSwipeDirection.RIGHT)
            }
            override fun onSwipeUp() {
                nuimo.swipe(NuimoSwipeDirection.UP)
            }
            override fun onSwipeDown() {
                nuimo.swipe(NuimoSwipeDirection.DOWN)
            }
        })

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
}

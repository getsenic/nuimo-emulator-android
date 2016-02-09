package com.senic.nuimo.emulator

import android.bluetooth.BluetoothDevice
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import butterknife.bindView

class MainActivity : AppCompatActivity(), NuimoListener, NuimoView.GestureEventListener {
    companion object {
        val TAG = "Nuimo.MainActivity"
    }

    val nuimo: Nuimo by lazy{ Nuimo(this).apply{ addListener(this@MainActivity) } }

    val nuimoView: NuimoView by bindView(R.id.nuimo)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nuimoView.gestureEventListener = this

        //TODO: Add UI switch for power on/off
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

    override fun onReceiveLedMatrix(leds: BooleanArray, brightness: Float, displayInterval: Float) {
        runOnUiThread { nuimoView.displayLedMatrix(leds, brightness, displayInterval) }
    }

    /*
     * NuimoView.GestureEventListener
     */

    override fun onButtonPress() {
        nuimo.pressButton()
    }

    override fun onButtonRelease() {
        nuimo.releaseButton()
    }

    override fun onSwipe(direction: NuimoSwipeDirection) {
        nuimo.swipe(direction)
    }

    override fun onRotate(value: Float) {
        nuimo.rotate(value)
    }
}

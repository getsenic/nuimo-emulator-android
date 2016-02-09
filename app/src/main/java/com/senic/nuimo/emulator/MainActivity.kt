package com.senic.nuimo.emulator

import android.bluetooth.BluetoothDevice
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import butterknife.bindView

class MainActivity : AppCompatActivity(), NuimoListener, NuimoView.GestureEventListener {
    companion object {
        val TAG = "Nuimo.MainActivity"
    }

    val nuimo: Nuimo by lazy{ Nuimo(this).apply{ listener = this@MainActivity } }

    val nuimoView: NuimoView by bindView(R.id.nuimo)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nuimoView.gestureEventListener = this

        powerOn()
    }

    override fun onDestroy() {
        powerOff()

        super.onDestroy()
    }

    private fun powerOn() {
        nuimo.powerOn()
        nuimoView.isEnabled = true
        nuimoView.displayLedMatrix(intArrayOf(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 1, 1, 0, 1, 1, 0, 0,
                0, 1, 1, 1, 1, 1, 1, 1, 0,
                1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1,
                0, 1, 1, 1, 1, 1, 1, 1, 0,
                0, 0, 1, 1, 1, 1, 1, 0, 0,
                0, 0, 0, 1, 1, 1, 0, 0, 0,
                0, 0, 0, 0, 1, 0, 0, 0, 0).map { it == 1 }.toBooleanArray(), 1.0f, 0.0f)
    }

    private fun powerOff() {
        nuimo.powerOff()
        nuimoView.isEnabled = false
    }

    /*
     * NuimoListener
     */

    override fun onStartAdvertising() {
        //TODO: Provide UI feedback (e.g. some label)
    }

    override fun onStopAdvertising() {
        //TODO: Update UI
    }

    override fun onStartAdvertisingFailure(errorCode: Int) {
        runOnUiThread { Toast.makeText(this, "BLE advertising not supported by this device", Toast.LENGTH_LONG).show() }
    }

    override fun onConnect(device: BluetoothDevice) {
        runOnUiThread { Toast.makeText(this, "Connected to ${device.address}", Toast.LENGTH_LONG).show() }
    }

    override fun onDisconnect(device: BluetoothDevice) {
        runOnUiThread { Toast.makeText(this, "Disconnected from ${device.address}", Toast.LENGTH_LONG).show() }
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

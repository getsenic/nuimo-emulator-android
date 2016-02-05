package com.senic.nuimo.emulator

import android.bluetooth.BluetoothDevice
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast

class MainActivity : AppCompatActivity(), NuimoListener {

    val nuimo: Nuimo by lazy{ Nuimo(this).apply{ addListener(this@MainActivity) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        Toast.makeText(this, "Connected to ${device.address}", Toast.LENGTH_SHORT).show()
    }

    override fun onDisconnect(device: BluetoothDevice) {
        Toast.makeText(this, "Disconnected from ${device.address}", Toast.LENGTH_SHORT).show()
    }
}

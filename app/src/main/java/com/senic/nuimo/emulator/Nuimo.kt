/**
 *  Created by Lars Blumberg on 2/5/16.
 *  Copyright © 2015 Senic. All rights reserved.
 *
 *  This software may be modified and distributed under the terms
 *  of the MIT license.  See the LICENSE file for details. *
 */

package com.senic.nuimo.emulator

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.ParcelUuid
import android.util.Log
import java.util.*

class Nuimo(val context: Context) {
    companion object {
        val TAG = "Nuimo"
        val MAX_ROTATION_EVENTS_PER_SEC = 10
        val SINGLE_ROTATION_VALUE = 2800
    }

    val name = "Nuimo"

    private val listeners = ArrayList<NuimoListener>()
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val advertiser = adapter?.bluetoothLeAdvertiser
    private val advertiserListener = NuimoAdvertiseCallback()
    private val gattServer: BluetoothGattServer? = manager.openGattServer(context, NuimoGattServerCallback());
    private var isAdvertising = false
    private var connectedDevice: BluetoothDevice? = null
    private var subscribedCharacteristics = HashMap<UUID, BluetoothGattCharacteristic>()
    private var accumulatedRotationValue = 0.0f
    private var lastRotationEventNanos = System.nanoTime()

    fun addListener(listener: NuimoListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: NuimoListener) {
        listeners.remove(listener)
    }

    fun powerOn() {
        if (adapter == null || advertiser == null || gattServer == null) {
            //TODO: Notify listener about error
            return
        }

        reset()

        adapter.name = name

        NUIMO_SERVICE_UUIDS.forEach {
            gattServer.addService(BluetoothGattService(it, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                NUIMO_CHARACTERISTIC_UUIDS_FOR_SERVICE_UUID[it]!!.forEach {
                    val properties = PROPERTIES_FOR_CHARACTERISTIC_UUID[it]!!
                    val permissions = PERMISSIONS_FOR_CHARACTERISTIC_UUID[it]!!
                    addCharacteristic(BluetoothGattCharacteristic(it, properties, permissions).apply {
                        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                            addDescriptor(BluetoothGattDescriptor(CHARACTERISTIC_NOTIFICATION_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE))}})}})}
    }

    fun powerOff() {
        reset()
    }

    private fun reset() {
        Log.i(TAG, "RESET")
        stopAdvertising()
        accumulatedRotationValue = 0.0f
        subscribedCharacteristics.clear()
        gattServer?.clearServices()
        if (connectedDevice != null) {
            disconnect(connectedDevice!!)
            connectedDevice = null
        }
        Log.i(TAG, "SERVICE COUNT = " + gattServer?.services?.size)
    }

    private fun disconnect(device: BluetoothDevice) {
        gattServer?.cancelConnection(device)
        subscribedCharacteristics.clear()
        listeners.forEach { it.onDisconnect(device) }
    }

    /*
     * User input
     */

    fun pressButton() {
        if (connectedDevice == null) return
        val characteristic = subscribedCharacteristics[SENSOR_BUTTON_CHARACTERISTIC_UUID] ?: return
        characteristic.value = byteArrayOf(1)
        gattServer?.notifyCharacteristicChanged(connectedDevice, characteristic, false)
    }

    fun releaseButton() {
        if (connectedDevice == null) return
        val characteristic = subscribedCharacteristics[SENSOR_BUTTON_CHARACTERISTIC_UUID] ?: return
        characteristic.value = byteArrayOf(0)
        gattServer?.notifyCharacteristicChanged(connectedDevice, characteristic, false)
    }

    fun swipe(direction: NuimoSwipeDirection) {
        if (connectedDevice == null) return
        val characteristic = subscribedCharacteristics[SENSOR_TOUCH_CHARACTERISTIC_UUID] ?: return
        characteristic.value = byteArrayOf(direction.gattByte)
        gattServer?.notifyCharacteristicChanged(connectedDevice, characteristic, false)
    }

    fun rotate(value: Float) {
        if (connectedDevice == null) return
        val characteristic = subscribedCharacteristics[SENSOR_ROTATION_CHARACTERISTIC_UUID] ?: return
        accumulatedRotationValue += value
        when {
            accumulatedRotationValue == 0.0f -> return
            1.000000000f / (System.nanoTime() - lastRotationEventNanos) > MAX_ROTATION_EVENTS_PER_SEC -> return
        }
        val valueToSend = (SINGLE_ROTATION_VALUE * accumulatedRotationValue).toInt()
        characteristic.setValue(valueToSend, BluetoothGattCharacteristic.FORMAT_SINT16, 0)
        gattServer?.notifyCharacteristicChanged(connectedDevice, characteristic, false)
        //TODO: Reset only if notification was sent
        accumulatedRotationValue = 0.0f
        lastRotationEventNanos = System.nanoTime()
    }

    /*
     * Bluetooth GATT Handling
     */

    private fun startAdvertising() {
        if (advertiser == null) return
        if (isAdvertising) return
        isAdvertising = true

        Log.i(TAG, "START ADVERTISING")

        val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

        val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(SENSOR_SERVICE_UUID))
                .build()

        advertiser.startAdvertising(settings, data, advertiserListener)
    }

    private fun stopAdvertising() {
        if (!isAdvertising) return
        isAdvertising = false
        if (advertiser == null) return

        Log.i(TAG, "STOP ADVERTISING")

        advertiser.stopAdvertising(advertiserListener)
    }

    private inner class NuimoAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.i(TAG, "Cannot advertise, error: $errorCode")
            //TODO: Notify listener
        }
    }

    private inner class NuimoGattServerCallback : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            Log.i(TAG, "SERVICE ${service.uuid} ADDED, count= " + gattServer?.services?.size)
            if (gattServer?.services?.size == NUIMO_SERVICE_UUIDS.size) {
                startAdvertising()
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.i(TAG, "Connection state changed for device ${device.address} new state: $newState")
            // Only allow one connection, refuse all other connection requests
            if (connectedDevice != null && connectedDevice != device) {
                gattServer?.cancelConnection(device)
                return
            }
            val previousConnectedDevice = connectedDevice
            when {
                status   != BluetoothGatt.GATT_SUCCESS        -> connectedDevice = null
                newState == BluetoothGatt.STATE_CONNECTING    -> stopAdvertising()
                newState == BluetoothGatt.STATE_CONNECTED     -> connectedDevice = device
                newState == BluetoothGatt.STATE_DISCONNECTING -> connectedDevice = null
                newState == BluetoothGatt.STATE_DISCONNECTED  -> connectedDevice = null
            }
            when {
                connectedDevice != null && previousConnectedDevice == null -> listeners.forEach { it.onConnect(connectedDevice!!) }
                connectedDevice == null && previousConnectedDevice != null -> { disconnect(previousConnectedDevice); startAdvertising() }
                connectedDevice == null && previousConnectedDevice == null -> startAdvertising()
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            Log.i(TAG, "onCharacteristicReadRequest ${characteristic.uuid}, $requestId")
            when (characteristic.uuid) {
                BATTERY_CHARACTERISTIC_UUID -> {
                    Log.i(TAG, "SEND BATTERY READ RESPONSE")
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf(getBatteryLevel().toByte()))
                }
                else -> {
                    Log.i(TAG, "SEND UNKNOWN READ RESPONSE")
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, byteArrayOf())
                }
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            Log.i(TAG, "onCharacteristicWriteRequest ${characteristic.uuid}, $requestId, responseNeeded=$responseNeeded")
            when (characteristic.uuid) {
                LED_MATRIX_CHARACTERISTIC_UUID -> {
                    Log.i(TAG, "SEND MATRIX WRITE RESPONSE")
                    val errorCode = when {
                        !responseNeeded                   -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                        value == null || value.size != 13 -> BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                        offset != 0                       -> BluetoothGatt.GATT_INVALID_OFFSET
                        else                              -> BluetoothGatt.GATT_SUCCESS
                    }
                    gattServer?.sendResponse(device, requestId, errorCode, 0, byteArrayOf())
                }
                else -> {
                    Log.i(TAG, "SEND UNKNOWN WRITE RESPONSE")
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, 0, byteArrayOf())
                }
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            Log.i(TAG, "onDescriptorReadRequest ${descriptor.characteristic.uuid}, $requestId")
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            Log.i(TAG, "onDescriptorWriteRequest ${descriptor.characteristic.uuid}, $requestId")

            val responseStatus = when {
                (PROPERTIES_FOR_CHARACTERISTIC_UUID[descriptor.characteristic.uuid] ?: 0) and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 -> BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                value == null                                                         -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                value.equalsArray(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)  -> { subscribedCharacteristics[descriptor.characteristic.uuid] = descriptor.characteristic; BluetoothGatt.GATT_SUCCESS }
                value.equalsArray(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) -> { subscribedCharacteristics.remove(descriptor.characteristic.uuid); BluetoothGatt.GATT_SUCCESS }
                else                                                                  -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
            }
            gattServer?.sendResponse(device, requestId, responseStatus, 0, byteArrayOf())
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Log.i(TAG, "onNotificationSent")
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            Log.i(TAG, "onNotificationSent  $requestId, $execute")
            //TODO: Call gattServer?.sendResponse()
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.i(TAG, "onMtuChanged  $mtu")
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level == -1 || scale == -1) { return 0 }
        return Math.min(100, Math.max(0, (level.toFloat() / scale.toFloat() * 100.0f).toInt()))
    }
}

enum class NuimoSwipeDirection(val gattValue: Int) {
    LEFT(0), RIGHT(1), UP(2), DOWN(3)
}

interface NuimoListener {
    fun onConnect(device: BluetoothDevice)
    fun onDisconnect(device: BluetoothDevice)
}

/*
 * Nuimo BLE GATT service and characteristic UUIDs
 */

private val BATTERY_SERVICE_UUID                        = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
private val BATTERY_CHARACTERISTIC_UUID                 = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
private val DEVICE_INFORMATION_SERVICE_UUID             = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
private val DEVICE_INFORMATION_CHARACTERISTIC_UUID      = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
private val LED_MATRIX_SERVICE_UUID                     = UUID.fromString("f29b1523-cb19-40f3-be5c-7241ecb82fd1")
private val LED_MATRIX_CHARACTERISTIC_UUID              = UUID.fromString("f29b1524-cb19-40f3-be5c-7241ecb82fd1")
private val SENSOR_SERVICE_UUID                         = UUID.fromString("f29b1525-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_FLY_CHARACTERISTIC_UUID              = UUID.fromString("f29b1526-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_TOUCH_CHARACTERISTIC_UUID            = UUID.fromString("f29b1527-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_ROTATION_CHARACTERISTIC_UUID         = UUID.fromString("f29b1528-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_BUTTON_CHARACTERISTIC_UUID           = UUID.fromString("f29b1529-cb19-40f3-be5c-7241ecb82fd2")
private val CHARACTERISTIC_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

val NUIMO_SERVICE_UUIDS = arrayOf(
        BATTERY_SERVICE_UUID,
        DEVICE_INFORMATION_SERVICE_UUID,
        LED_MATRIX_SERVICE_UUID,
        SENSOR_SERVICE_UUID
)

val NUIMO_CHARACTERISTIC_UUIDS_FOR_SERVICE_UUID = mapOf(
        BATTERY_SERVICE_UUID                   to arrayOf(BATTERY_CHARACTERISTIC_UUID),
        DEVICE_INFORMATION_SERVICE_UUID        to arrayOf(DEVICE_INFORMATION_CHARACTERISTIC_UUID),
        LED_MATRIX_SERVICE_UUID                to arrayOf(LED_MATRIX_CHARACTERISTIC_UUID),
        SENSOR_SERVICE_UUID                    to arrayOf(SENSOR_FLY_CHARACTERISTIC_UUID, SENSOR_TOUCH_CHARACTERISTIC_UUID, SENSOR_ROTATION_CHARACTERISTIC_UUID, SENSOR_BUTTON_CHARACTERISTIC_UUID)
)

val PROPERTIES_FOR_CHARACTERISTIC_UUID = mapOf(
        BATTERY_CHARACTERISTIC_UUID            to (BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY),
        DEVICE_INFORMATION_CHARACTERISTIC_UUID to BluetoothGattCharacteristic.PROPERTY_READ,
        LED_MATRIX_CHARACTERISTIC_UUID         to BluetoothGattCharacteristic.PROPERTY_WRITE,
        SENSOR_FLY_CHARACTERISTIC_UUID         to BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        SENSOR_TOUCH_CHARACTERISTIC_UUID       to BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        SENSOR_ROTATION_CHARACTERISTIC_UUID    to BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        SENSOR_BUTTON_CHARACTERISTIC_UUID      to BluetoothGattCharacteristic.PROPERTY_NOTIFY
)

val PERMISSIONS_FOR_CHARACTERISTIC_UUID = mapOf(
        BATTERY_CHARACTERISTIC_UUID            to (BluetoothGattCharacteristic.PERMISSION_READ),
        DEVICE_INFORMATION_CHARACTERISTIC_UUID to BluetoothGattCharacteristic.PERMISSION_READ,
        LED_MATRIX_CHARACTERISTIC_UUID         to BluetoothGattCharacteristic.PERMISSION_WRITE,
        SENSOR_FLY_CHARACTERISTIC_UUID         to BluetoothGattCharacteristic.PERMISSION_READ,
        SENSOR_TOUCH_CHARACTERISTIC_UUID       to BluetoothGattCharacteristic.PERMISSION_READ,
        SENSOR_ROTATION_CHARACTERISTIC_UUID    to BluetoothGattCharacteristic.PERMISSION_READ,
        SENSOR_BUTTON_CHARACTERISTIC_UUID      to BluetoothGattCharacteristic.PERMISSION_READ
)

fun ByteArray.equalsArray(other: ByteArray) = Arrays.equals(this, other)
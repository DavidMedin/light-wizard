package com.davidmedin.lightwizard

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.util.Log

class LightWizardGatt(bluetooth_device : BluetoothDevice, activity : MainActivity) {
    // TODO: Don't use a string, make an actual UUID object.
    val light_switch_service_id = "0ca2d9fa-785e-4811-a8c2-d4233409d79f"
    val wizard_char_switch_id = "e11d545a-908c-4e0d-b765-97d13c33aa50"


    val GATTCallbacks : BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                println("BLE connected to GATT")
                startServiceDiscovery(gatt)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                println("BLE disconnected from GATT")
            }
        }

        @SuppressLint("MissingPermission")
        fun startServiceDiscovery(gatt: BluetoothGatt?) {
            // TODO: check for permisisons.
            if ( gatt?.discoverServices() == false ) {
                Log.w("GATT", "Failed to start service discovery.")
            }
        }

        fun getLightSwitchChar(service : BluetoothGattService) : BluetoothGattCharacteristic? {
            for(char in service.characteristics) {
                if(char.uuid.toString() == wizard_char_switch_id) { //TODO: Bad
                    return char
                }
            }
            return null;
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                println("BLE found GATT Services : ${gatt?.services}")

//                 Find the ble service for switching the light
                    for ( service in gatt!!.services) {
                        if (service.uuid!!.toString() == light_switch_service_id) {
                            light_switch_service = service
                            val switch_char = getLightSwitchChar(service)
                            if(switch_char != null) {
                                gatt.writeCharacteristic(switch_char, byteArrayOf(0), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                                println("toggled the characteristic.")
                            }else {
                                Log.w("Char", "Failed to find characteristic.")
                            }
                        }
                    }
            } else {
                Log.w("GATT", "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicWrite (gatt : BluetoothGatt, characteristic : BluetoothGattCharacteristic, status : Int) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                println("Successfully wrote a characteristic")
            }else {
                Log.e("GATT Char", "Failed to write a characteristic")
            }
        }
    }

    @SuppressLint("MissingPermission") // TODO: Get rid of this.
    val gatt = bluetooth_device.connectGatt(activity.applicationContext,false, GATTCallbacks)
    var light_switch_service : BluetoothGattService? = null
}
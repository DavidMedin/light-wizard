import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import com.davidmedin.lightwizard.MainActivity

import androidx.compose.runtime.Composable
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@RequiresApi(Build.VERSION_CODES.S)
class AndroidPlatform constructor(private val activity : MainActivity) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val hasBluetooth: Boolean = true
    override var state: String = ""

    init {
        // not actually needed since the app manifest says 'I require bluetooth LE. Don't install unless you have it pls'.
        val packageManager = activity.applicationContext.packageManager
        val bluetoothLEAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        println("Bluetooth LE available : $bluetoothLEAvailable")
    }

    val bluetoothManager = getSystemService(activity.applicationContext, BluetoothManager::class.java)!!
    val adapter = bluetoothManager.adapter!!



    val wizardScanner = adapter.bluetoothLeScanner
    var scanning = false
    val handler = Handler(Looper.getMainLooper()) // Used to run a code block in X seconds.

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000

    // Used by toggleScanForWizard.
    val scanCallback : ScanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission") // Should only be called by toggleScanForWizard, which checks.
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

//            if(result.device.name != null) {
//                println("Found BLE device : ${result.device.name}")
//            }
            if( isBLEDeviceAWizard(result.device) ) {
                lightwizard_device = result.device
                Log.i("BLE Scanner","A suitable wizard has been found")
            }
        }

    }

    // Toggle the BLE device scan for the Light Wizard.
    fun toggleScanForWizard() {
        if(!scanning) {
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                state = "no perms"
                return // TODO: Return some sort of UI error saying "no perms"
            }
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                state = "no perms"
                return // TODO: Return some sort of UI error saying "no perms"
            }
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                state = "no perms"
                return // TODO: Return some sort of UI error saying "no perms"
            }

            // Stop scan in a few seconds.
            handler.postDelayed( {
                // Code block - will run in SCAN_PERIOD ms.
                scanning = false
                wizardScanner.stopScan(scanCallback)
                println("Done scanning BLE.")
                // ==========
            } , SCAN_PERIOD)

            // Start scan.
            scanning = true
            wizardScanner.startScan(scanCallback)
        }else {
            // Currently scanning, stop the scan.
            scanning = false
            wizardScanner.stopScan(scanCallback)
        }
    }

    val GATTCallbacks : BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                println("BLE connected to GATT")
                startServiceDiscovery(gatt)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                println("BLE disconnected from GATT")
                lightwizard_gatt = null
            }
        }

        fun startServiceDiscovery(gatt: BluetoothGatt?) {
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("GATT", "Not enough perms for service discovery.")
                state = "no perms"
                return // TODO: Return some sort of UI error saying "no perms"
            }

            if ( gatt?.discoverServices() == false ) {
                Log.w("GATT", "Failed to start service discovery.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
//                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
                println("BLE found GATT Services : ${gatt?.services}")
                state = "Discovered Services!"

                // Find the ble service for switching the light
                for ( service in gatt!!.services) {
                    if (service.uuid!!.toString() == wizard_service_id) {
                        wizard_service = service
                    }
                }
                if (wizard_service == null) {
                    Log.w("GATT", "No service in BLE device for switching lights.")
                    return
                }

            } else {
                Log.w("GATT", "onServicesDiscovered received: $status")
            }
        }


    }
    var lightwizard_device : BluetoothDevice? = null // BLE device
    var lightwizard_gatt : BluetoothGatt? = null // BLE device connection instance
    var wizard_service :  BluetoothGattService? = null // BLE service for switching lights

    // TODO: Don't use a string, make an actual UUID object.
    val wizard_service_id = "0ca2d9fa-785e-4811-a8c2-d4233409d79f"
    val wizard_char_switch_id = "e11d545a-908c-4e0d-b765-97d13c33aa50"

    override fun bluetoothEnabled(): Boolean {
        return adapter.isEnabled
    }

    @SuppressLint("MissingPermission") // I'm promising that future David will remember to check permission before calling this function.
    fun isBLEDeviceAWizard(device : BluetoothDevice): Boolean {
        return device.name == "Light Wizard" // || device.name == "Arduino"
    }

    fun getWizardFromBondedBLEDevices() : BluetoothDevice? {
        if ( lightwizard_device != null ) {
            return lightwizard_device// Already connected to wizard, just leave.
        }

        // Check if we have the bluetooth connect permission.
        if (ActivityCompat.checkSelfPermission(
                activity.applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            state = "no perms"
            return null// TODO: Return some sort of UI error saying "no perms"
        }

        for (device in adapter.bondedDevices) {
            Log.i("Bonded BLE Device: ", device.name)
            if(isBLEDeviceAWizard(device)){
//                lightwizard_gatt = device.connectGatt(activity.applicationContext,false, GATTCallbacks)
                return device
            }
        }
        return null
    }

    @SuppressLint("MissingPermission") // The BLE permissions should be met by the time 'device' is not null.
    @Composable
    fun showBLEDevice(device : BluetoothDevice?) {
        if( device != null ) {
            Text(device!!.name)
        } else {
            Text("No wizard has been found :(")
        }
    }

    @Composable
    override fun doBluetoothThings() {
        // probably need to use produceState .
        val wizard_device_state = remember { mutableStateOf(lightwizard_device) }
        println("Starting to do bluetooth things")
        wizard_device_state.value = getWizardFromBondedBLEDevices()
        if ( wizard_device_state.value == null ) {
            // There is no wizard that is bonded, time to find it.
            println("No wizard is bonded, scanning...")
            toggleScanForWizard()
            // Somehow wait for the wizard to be found
        }

        Text("Am scanning? : $is_scanning")
        Button(onClick = {toggleScanForWizard()}) {
            Text("Toggle Scan")
        }
        showBLEDevice(wizard_device_state.value)

    }

    @Composable
    override fun requestPermissions() {

        Button(onClick = {
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activity.requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }

        } ) {
            Text("Request BLE Connect Permissions")
        }


        Button(onClick = {
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activity.requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
            }

        } ) {
            Text("Request BLE Scan Permissions")
        }


        Button(onClick = {
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activity.requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        } ) {
            Text("Request Fine Location Permissions")
        }

    }
}

//actual fun getPlatform(): Platform = AndroidPlatform()
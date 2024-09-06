import android.Manifest
import android.app.Activity
import android.os.Build
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import com.davidmedin.lightwizard.MainActivity

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
                    if (service.uuid!!.toString() == "0ca2d9fa-785e-4811-a8c2-d4233409d79f") {
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
    var lightwizard_gatt : BluetoothGatt? = null // BLE device
    var wizard_service :  BluetoothGattService? = null // BLE service for switching lights

    override fun bluetoothEnabled(): Boolean {
        return adapter.isEnabled
    }

    fun connectToWizard() {
        if ( lightwizard_gatt != null ) {
            return // Already connected to wizard, just leave.
        }

        // Check if we have the bluetooth connect permission.
        if (ActivityCompat.checkSelfPermission(
                activity.applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            state = "no perms"
            return // TODO: Return some sort of UI error saying "no perms"
        }

        for (device in adapter.bondedDevices) {
            Log.i("BLE Device: ", device.name)
            if(device.name == "Lightwizard" || device.name == "Arduino"){
                lightwizard_gatt = device.connectGatt(activity.applicationContext,false, GATTCallbacks)
            }
        }
    }

    override fun doBluetoothThings() {

        connectToWizard()
//        state = bonded_devices.toString()
    }

    override fun requestPermissions() {
        if (ActivityCompat.checkSelfPermission(
                activity.applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}

//actual fun getPlatform(): Platform = AndroidPlatform()
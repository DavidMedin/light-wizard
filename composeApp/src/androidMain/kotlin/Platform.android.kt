import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import com.davidmedin.lightwizard.MainActivity

import androidx.compose.runtime.Composable
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.davidmedin.lightwizard.LightWizardGatt
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.cancellation.CancellationException

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

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 12000

    // Used by toggleScanForWizard.


    // Toggle the BLE device scan for the Light Wizard.
    suspend fun scanForWizard() : BluetoothDevice? {
        val found_devices : MutableList<BluetoothDevice> = mutableListOf()
        val scanCallback : ScanCallback = object : ScanCallback() {

            @SuppressLint("MissingPermission") // Should only be called by toggleScanForWizard, which checks.
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                if(result.device.name != null) {
                    println("Found BLE device : ${result.device.name}")
                    found_devices.add(result.device)
                }
            }

        }

        if(!scanning) {
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                state = "no perms"
                return null // TODO: Return some sort of UI error saying "no perms"
            }
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                state = "no perms"
                return null // TODO: Return some sort of UI error saying "no perms"
            }
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                state = "no perms"
                return null // TODO: Return some sort of UI error saying "no perms"
            }

            // Start scan.
            // Aiden says that bluetooth scanning is weird and you need to do this sometimes.
            wizardScanner.startScan(scanCallback)
            wizardScanner.stopScan(scanCallback)

            scanning = true
            wizardScanner.startScan(scanCallback)

            // wait
            delay(SCAN_PERIOD)

            // stop scan
            scanning = false
            wizardScanner.stopScan(scanCallback)
            println("Done scanning BLE.")
            println("Extracting found devices...")
            var wizard : BluetoothDevice? = null
            found_devices.forEach {
                println(it)
                if( isBLEDeviceAWizard(it) ) {
                    println("Found wizard")
                    wizard = it
                }
            }
            return wizard
        }else {
            // Currently scanning, stop the scan.
            scanning = false
            wizardScanner.stopScan(scanCallback)
        }
        return null
    }


//    var wizard_service :  BluetoothGattService? = null // BLE service for switching lights


    override fun bluetoothEnabled(): Boolean {
        return adapter.isEnabled
    }

    @SuppressLint("MissingPermission") // I'm promising that future David will remember to check permission before calling this function.
    fun isBLEDeviceAWizard(device : BluetoothDevice): Boolean {
        return device.name == "Light Wizard" // || device.name == "Arduino"
    }

    fun getWizardFromBondedBLEDevices() : BluetoothDevice? {
//        if ( lightwizard_device != null ) {
//            return lightwizard_device// Already connected to wizard, just leave.
//        }

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

    // I Don't know how this works TODO: figure it out
    sealed class Result<out T> {
        object Loading : Result<Nothing>()
        object Error : Result<Nothing>()
        data class Success<R>(val r: R) : Result<R>()
    }

    @Composable
    fun getWizardDevice() : State<Result<BluetoothDevice>> {
        return produceState<Result<BluetoothDevice>>(initialValue = Result.Loading ) {
            var wizard_device : BluetoothDevice? = getWizardFromBondedBLEDevices()
            if ( wizard_device == null ) {
                // There is no wizard that is bonded, time to find it.
                println("No wizard is bonded, scanning...")
                wizard_device = scanForWizard() // May yield the coroutine

                if (wizard_device == null) {
                    value = Result.Error
                }else {
                    value = Result.Success(wizard_device)
                }
            }else {
                value = Result.Success(wizard_device)
            }
        }
    }

//    @Composable fun getDeviceGatt(device : BluetoothDevice) : State<Result<List<BluetoothGattService>>> {
//        var lightwizard_gatt : BluetoothGatt? = null
//        val flow = callbackFlow<List<BluetoothGattService>> {
//            val GATTCallbacks : BluetoothGattCallback = object : BluetoothGattCallback() {
//                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
//                    if (newState == BluetoothProfile.STATE_CONNECTED) {
//                        // successfully connected to the GATT Server
//                        println("BLE connected to GATT")
//                        startServiceDiscovery(gatt)
//                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//                        // disconnected from the GATT Server
//                        println("BLE disconnected from GATT")
//                        cancel(CancellationException("BLE disconnected from GATT"))
//                    }
//                }
//
//                fun startServiceDiscovery(gatt: BluetoothGatt?) {
//                    if (ActivityCompat.checkSelfPermission(
//                            activity.applicationContext,
//                            Manifest.permission.BLUETOOTH_CONNECT
//                        ) != PackageManager.PERMISSION_GRANTED
//                    ) {
//                        Log.w("GATT", "Not enough perms for service discovery.")
//                        cancel(CancellationException("Not enough perms for service discovery."))
//                        return // TODO: Return some sort of UI error saying "no perms"
//                    }
//
//                    if ( gatt?.discoverServices() == false ) {
//                        Log.w("GATT", "Failed to start service discovery.")
//                        cancel(CancellationException("Failed to start service discovery."))
//                    }
//                }
//
//                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
//                    if (status == BluetoothGatt.GATT_SUCCESS) {
//                        println("BLE found GATT Services : ${gatt?.services}")
//
//                                // Find the ble service for switching the light
//        //                for ( service in gatt!!.services) {
//        //                    if (service.uuid!!.toString() == wizard_service_id) {
//        //                        wizard_service = service
//        //                    }
//        //                }
//                        trySendBlocking(gatt!!.services).onFailure { throwable ->
//                            Log.e("GATT Callback", "Failed to send services to Compose : $throwable")
//                        }
//
//                    } else {
//                        Log.w("GATT", "onServicesDiscovered received: $status")
//                        cancel(CancellationException("onServicesDiscovered received: $status")) // TODO: Why does it say 'exception is not thrown'?
//                    }
//                }
//            }
//            //not going to do anything with this gatt, just store it so it isn't garbage collect.
//            lightwizard_gatt = device.connectGatt(activity.applicationContext,false, GATTCallbacks)
//            awaitClose {}
//        }
//
//        val state = produceState<Result<List<BluetoothGattService>>>(initialValue = Result.Loading) {
//            flow.collect { new_services -> value = Result.Success(new_services) }
//        }
//
//        return Pair(lightwizard_gatt!!, state)
//    }



    @Composable fun showDeviceGatt(device : BluetoothDevice) {

//        val res_services = getDeviceGatt(device)
//        when(res_services.value) {
//            is Result.Loading -> {
//                Text("Loading services...")
//            }
//
//            Result.Error -> {
//                Text("Failed to load services...")
//            }
//            is Result.Success -> {
//                val (services) = res_services.value as Result.Success<List<BluetoothGattService>>
//                for(service in services) {
//                    Text(service.toString())
//                    for(char in service.characteristics) {
//                        Text(char.toString()) // TODO: Add info to make more than one instance of 'Text'
//                    }
//                }
//
//
//            }
//        }
        val gatt = LightWizardGatt(device, activity)
        Text("hi")
    }

    @Composable
    override fun doBluetoothThings() {
        // probably need to use produceState .
        println("Starting to do bluetooth things")


        // button that says "scan"
        // when pressed, starts scanning, text that says "scanning"
        var show_content by remember { mutableStateOf(false) }
        Button(onClick = {show_content = true}) {
            Text("Toggle Scan")
        }
        if(show_content) {
            val wizard_device = getWizardDevice()
            when(wizard_device.value){
                is Result.Loading -> {
                    Text("Scanning...")
                }
                Result.Error -> {
                    Text("Failed!")
                }
                is Result.Success -> {
                    val (device) = wizard_device.value as Result.Success<BluetoothDevice>
                    showBLEDevice(device = device)
//                    val device_gatt = getDeviceGatt(device)
//                    val char = BluetoothGattCharacteristic(UUID.fromString(wizard_char_switch_id), PERMISSION_WRITE, PROPERTY_WRITE)
//                    device_gatt.writeCharacteristic(char, byteArrayOf(0,0), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    showDeviceGatt(device = device)
                }
            }

        }

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
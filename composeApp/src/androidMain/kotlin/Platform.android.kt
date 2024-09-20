import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay

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

    open class MissingPermissionsException(s: String) : RuntimeException("Missing Android Permission : $s")
    class MissingBTScanPermissionException : MissingPermissionsException("Bluetooth Scan")
    class MissingBTConnectPermissionException : MissingPermissionsException("Bluetooth Connect")
    class MissingAccessFineLocationException : MissingPermissionsException("Access Fine Location")

    // Toggle the BLE device scan for the Light Wizard.
    suspend fun scanForWizard() : BluetoothDevice? {
        println("Running scanForWizard...")
        val found_devices : MutableList<BluetoothDevice> = mutableListOf()
        val scanCallback : ScanCallback = object : ScanCallback() {

            @SuppressLint("MissingPermission") // Should only be called by toggleScanForWizard, which checks.
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
//                println("Found device : ${result.device.name}")
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
                println("Failed to scan, no perms.")
                throw MissingBTScanPermissionException()
            }
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                println("Failed to scan, no perms.")
                throw MissingAccessFineLocationException()
            }
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                println("Failed to scan, no perms.")
                throw MissingBTConnectPermissionException()
            }

            // Start scan.
            // Aiden says that bluetooth scanning is weird and you need to do this sometimes.
//            wizardScanner.startScan(scanCallback)
//            wizardScanner.stopScan(scanCallback)

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
        data class Error<out E:Exception>(val exception: E?) : Result<Nothing>()
        data class Success<R>(val r: R) : Result<R>()
    }

    @Composable
    fun getWizardDevice() : State<Result<BluetoothDevice>> {
        return produceState<Result<BluetoothDevice>>(initialValue = Result.Loading ) {
            var wizard_device : BluetoothDevice? = getWizardFromBondedBLEDevices()
            if ( wizard_device == null ) {
                // There is no wizard that is bonded, time to find it.
                println("No wizard is bonded, scanning...")

                // Return the result as Success with the device or Error with an exception or null.
                try {
                    wizard_device = scanForWizard()
                    if(wizard_device != null ){
                        value = Result.Success(wizard_device)
                    }else {
                        value = Result.Error(null)
                    }
                } catch(e : Exception) {
                    value = Result.Error(e)
                }

            }else {
                value = Result.Success(wizard_device)
            }
        }
    }


    @Composable fun showDeviceGatt(device : BluetoothDevice) {
        val gatt = LightWizardGatt(device, activity)
        Text("hi")
    }

    @Composable
    override fun doBluetoothThings() {
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
                is Result.Error<*> -> {
                    val (exception: Exception?) = wizard_device.value as Result.Error<Exception>
                    Text("Failed to Scan : ${exception?.message}")
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

    fun HasAllPermissions() : Boolean {
        return ActivityCompat.checkSelfPermission(
            activity.applicationContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
                &&
                ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                &&
                ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
    }

    @Composable
    override fun hasPermissions(): State<Boolean> {
        return produceState<Boolean>(initialValue = false) {

            // If the app already has required permissions, don't wait for them to be accepted.
            if(HasAllPermissions()){
                value = true
                return@produceState
            }

            // Wait for all Deferred values to complete (wait for permission to be accepted or denied).
            awaitAll(btConnectGranted, accessFineLocationGranted)
            if(HasAllPermissions()){
                value = true
                return@produceState
            }


        }

    }

    // Completes when either both BT Connect and BT Scan succeed or one fails.
    var btConnectGranted : CompletableDeferred<Boolean> = CompletableDeferred()
    val ReqBluetoothConnectLauncher = activity.registerForActivityResult( ActivityResultContracts.RequestPermission() ) {
        // This is some weird-ass syntax, but this is a callback argument for registerForActivityResult.
        isGranted : Boolean -> run {
            Log.i("Permission: ", "is $isGranted")
            // If is granted, go to next permission stage, otherwise, fail.
            if( isGranted ){
                ReqBluetoothScanLauncher.launch(android.Manifest.permission.BLUETOOTH_SCAN)
            } else {
                btConnectGranted.complete(false)
            }
        }
    }

    val ReqBluetoothScanLauncher = activity.registerForActivityResult( ActivityResultContracts.RequestPermission() ) {
        // This is some weird-ass syntax, but this is a callback argument for registerForActivityResult.
            isGranted : Boolean -> run {
        Log.i("Permission: ", "is $isGranted")
        btConnectGranted.complete(isGranted)
    }
    }


    var accessFineLocationGranted : CompletableDeferred<Boolean> = CompletableDeferred()
    val ReqAccessFineLocationLauncher = activity.registerForActivityResult( ActivityResultContracts.RequestPermission() ) {
        // This is some weird-ass syntax, but this is a callback argument for registerForActivityResult.
        isGranted : Boolean -> run {
            Log.i("Permission: ", "is $isGranted")
            accessFineLocationGranted.complete(isGranted)
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
                ReqBluetoothConnectLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }

        } ) {
            Text("Request Bluetooth Permissions")
        }

        Button(onClick = {
            if (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ReqAccessFineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        } ) {
            Text("Request Location Permissions")
        }

    }
}


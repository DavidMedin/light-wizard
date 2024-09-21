import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
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
import com.juul.kable.Characteristic
import com.juul.kable.Filter
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.Transport
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Hex
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import com.juul.kable.peripheral
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

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


    // A coroutine scope that will have a bluetooth device bound to it.
    // If we need multiple bluetooth devices connected, we'll need multiple coroutine scopes.
    val big_coroutine_scope = CoroutineScope(Dispatchers.Default)
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
//    suspend fun scanForWizard() : BluetoothDevice? {
//        println("Running scanForWizard...")
//        val found_devices : MutableList<BluetoothDevice> = mutableListOf()
//        val scanCallback : ScanCallback = object : ScanCallback() {
//
//            @SuppressLint("MissingPermission") // Should only be called by toggleScanForWizard, which checks.
//            override fun onScanResult(callbackType: Int, result: ScanResult) {
//                super.onScanResult(callbackType, result)
////                println("Found device : ${result.device.name}")
//                if(result.device.name != null) {
//                    println("Found BLE device : ${result.device.name}")
//                    found_devices.add(result.device)
//                }
//            }
//
//        }
//
//        if(!scanning) {
//            if (ActivityCompat.checkSelfPermission(
//                    activity.applicationContext,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                println("Failed to scan, no perms.")
//                throw MissingBTScanPermissionException()
//            }
//            if (ActivityCompat.checkSelfPermission(
//                    activity.applicationContext,
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                println("Failed to scan, no perms.")
//                throw MissingAccessFineLocationException()
//            }
//            if (ActivityCompat.checkSelfPermission(
//                    activity.applicationContext,
//                    Manifest.permission.BLUETOOTH_CONNECT
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                println("Failed to scan, no perms.")
//                throw MissingBTConnectPermissionException()
//            }
//
//            // Start scan.
//            // Aiden says that bluetooth scanning is weird and you need to do this sometimes.
////            wizardScanner.startScan(scanCallback)
////            wizardScanner.stopScan(scanCallback)
//
//            scanning = true
//            wizardScanner.startScan(scanCallback)
//
//            // wait
//            delay(SCAN_PERIOD)
//
//            // stop scan
//            scanning = false
//            wizardScanner.stopScan(scanCallback)
//            println("Done scanning BLE.")
//            println("Extracting found devices...")
//            var wizard : BluetoothDevice? = null
//            found_devices.forEach {
//                println(it)
//                if( isBLEDeviceAWizard(it) ) {
//                    println("Found wizard")
//                    wizard = it
//                }
//            }
//            return wizard
//        }else {
//            // Currently scanning, stop the scan.
//            scanning = false
//            wizardScanner.stopScan(scanCallback)
//        }
//        return null
//    }
//


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
    fun getWizardPeripheral() : State<Result<Peripheral>> {
        return produceState<Result<Peripheral>>(initialValue = Result.Loading ) {
//            var wizard_device : BluetoothDevice? = getWizardFrom

            val scanner = Scanner {
                filters {
                    match {
                        name = Filter.Name.Exact("Light Wizard")
                    }
                }
                logging {
                    engine = SystemLogEngine
                    level = Logging.Level.Warnings
                    format = Logging.Format.Multiline
                }
            }
            val light_wiz_ad = scanner.advertisements.first() // <- suspends
            val light_wiz_peri = big_coroutine_scope.peripheral(light_wiz_ad) { // <- suspending
                // Peripheral config
                transport = Transport.Le
                logging {
                    engine = SystemLogEngine
                    level = Logging.Level.Warnings
                    format = Logging.Format.Multiline
                    data = Hex
                }
            }


            light_wiz_peri.connect() // <- suspending

            value = Result.Success(light_wiz_peri)


            light_wiz_peri.state.collect { state ->
                // Display and/or process the connection state.
                Log.w("Light wizard BLE state", "Changed connectivity state to $state")
            } // <- suspends indefinitely
        }
    }

    @Composable
    fun readPeripheralCharacteristic(peripheral : Peripheral, characteristic : Characteristic) : State<Result<ByteArray>> {
        return produceState<Result<ByteArray>>(initialValue = Result.Loading) {
            value = Result.Success(peripheral.read(characteristic))
        }
    }
//
//
//    @Composable fun showDeviceGatt(device : BluetoothDevice) {
//        val gatt = LightWizardGatt(device, activity)
//    }

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
            val wizard_peripheral = getWizardPeripheral()
            when(wizard_peripheral.value){
                is Result.Loading -> {
                    Text("Scanning...")
                }
                is Result.Error<*> -> {
                    val (exception: Exception?) = wizard_peripheral.value as Result.Error<Exception>
                    Text("Failed to Scan : ${exception?.message}")
                }
                is Result.Success -> {
                    val (periph) = wizard_peripheral.value as Result.Success<Peripheral>
//                    showBLEDevice(device = device)
////                    val device_gatt = getDeviceGatt(device)
////                    val char = BluetoothGattCharacteristic(UUID.fromString(wizard_char_switch_id), PERMISSION_WRITE, PROPERTY_WRITE)
////                    device_gatt.writeCharacteristic(char, byteArrayOf(0,0), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
//                    showDeviceGatt(device = device)

                    val switch_char = characteristicOf("0ca2d9fa-785e-4811-a8c2-d4233409d79f", "e11d545a-908c-4e0d-b765-97d13c33aa50")
                    val switch_state = readPeripheralCharacteristic(periph, switch_char)
                    when(switch_state.value) {
                        is Result.Error<*> -> {
                            val (exception: Exception?) = wizard_peripheral.value as Result.Error<Exception>
                            Text("Failed to read characteristic : ${exception?.message}")
                        }
                        Result.Loading -> {
                            Text("Loading...")
                        }
                        is Result.Success -> {
                            val (value) = switch_state.value as Result.Success<ByteArray>
                            Text("Switch state : $value")

                        }
                    }
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
                    Manifest.permission.ACCESS_COARSE_LOCATION
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
            accessGranted.await()
            if(HasAllPermissions()){
                value = true
                return@produceState
            }


        }

    }


    // has all access been granted.
    var accessGranted : CompletableDeferred<Boolean> = CompletableDeferred()

    data class Perm<T>(
        val permission: T,
        val sync: CompletableDeferred<Boolean>,
        val launcher: ActivityResultLauncher<T>
    )
    val perms = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    val launchers : MutableList<Perm<String>> = mutableListOf()
    init { // fill launchers with a triple of the permission, a sync object, and a launcher.
        perms.forEach() { perm ->
            var accessGranted : CompletableDeferred<Boolean> = CompletableDeferred()
            val requestLauncher = activity.registerForActivityResult( ActivityResultContracts.RequestPermission() ) { isGranted: Boolean ->
                run {
                    Log.i("$perm Permission: ", "is $isGranted")
                    accessGranted.complete(isGranted)
                }
            }
            launchers.add( Perm(perm,accessGranted, requestLauncher) )
        }
    }

    val locationPerms = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    val locationPerm : Perm<Array<String>> =
        run {
            var accessGranted: CompletableDeferred<Boolean> = CompletableDeferred()
            val requestLauncher =
                activity.registerForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    when {
                        permissions.getOrDefault(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            false
                        ) -> {
                            // Precise location access granted.
                            println("Location access granted")
                            accessGranted.complete(true)
                        }

                        permissions.getOrDefault(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            false
                        ) -> {
                            // Only approximate location access granted.
                            println("Only got coarse location access granted")
                            accessGranted.complete(false)
                        }

                        else -> {
                            // No location access granted.
                            println("Got no location access granted :(")
                            accessGranted.complete(false)
                        }
                    }
                }
            Perm(locationPerms, accessGranted, requestLauncher)
        }


    suspend fun requestPermission(perm : Perm<String>) : Boolean {
        if (ActivityCompat.checkSelfPermission(
                activity.applicationContext,
                perm.permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("${perm.permission} permission", "Already granted, not requesting.")
            return true
        }


        // The Android Activity can not quite be ready (still in onCreate) when this code runs.
        // Wait for the state to be STARTED before launching permissions. otherwise it explodes :)

        perm.launcher.launch(perm.permission)

        val isGranted = perm.sync.await() // <- Suspends
        return isGranted
    }

    override suspend fun requestPermissions() {

        var has_access = true
        for(perm in launchers) {
            val granted = requestPermission(perm)
            if(granted == false ){
                has_access = false
            }
        }
        if( locationPerms.filter { perm ->
            (ActivityCompat.checkSelfPermission(activity.applicationContext,perm) != PackageManager.PERMISSION_GRANTED)
        }.size != 0 ) {
            locationPerm.launcher.launch(locationPerms)
            if( locationPerm.sync.await() == false ) { // <- suspends
                has_access = false
            }
        }

        accessGranted.complete(has_access)
    }
}


import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.davidmedin.lightwizard.MainActivity

import androidx.compose.runtime.Composable
import androidx.compose.material.Button
import androidx.compose.material.Switch
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
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Hex
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import com.juul.kable.peripheral
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.S)
class AndroidPlatform(private val activity : MainActivity) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val hasBluetooth: Boolean = true
    override var state: String = ""


    // A coroutine scope that will have a bluetooth device bound to it.
    // If we need multiple bluetooth devices connected, we'll need multiple coroutine scopes.
    val big_coroutine_scope = CoroutineScope(Dispatchers.Default)

    override fun bluetoothEnabled(): Boolean {
        return true // Find a way with Kable to know if bluetooth is enabled.
    }



//     I Don't know how this works TODO: figure it out
    sealed class Result<out T> {
        object Loading : Result<Nothing>()
        data class Error<out E:Exception>(val exception: E?) : Result<Nothing>()
        data class Success<R>(val r: R) : Result<R>()
    }

    @Composable
    fun getWizardPeripheral() : State<Result<Peripheral>> {
        return produceState<Result<Peripheral>>(initialValue = Result.Loading ) {

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
            // Arduino code needs to be updated to support notifications.
//            peripheral.observe(characteristic).collect { data ->
//                value = Result.Success(data)
//            }
        }
    }
//

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

                    val switch_char = characteristicOf("0ca2d9fa-785e-4811-a8c2-d4233409d79f", "e11d545a-908c-4e0d-b765-97d13c33aa50")
                    val switch_state = readPeripheralCharacteristic(periph, switch_char) // <- produce state
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
                            var checked by remember { mutableStateOf(value[0] == 1.toByte()) }

                            Switch(
                                checked = checked,
                                onCheckedChange = {
                                    // Write to the remote.
                                    val newval = if(checked) { 0 } else { 1 }
                                    big_coroutine_scope.launch {
                                        periph.write(switch_char, byteArrayOf(newval.toByte()), writeType = WriteType.WithResponse)
                                        checked = periph.read(switch_char)[0] == 1.toByte()
                                    }
                                }
                            )

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

            // TODO: Total permission failure.
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

    val locationPerms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
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


        if(locationPerms.filter { perm ->
                (ActivityCompat.checkSelfPermission(
                    activity.applicationContext,
                    perm
                ) != PackageManager.PERMISSION_GRANTED)
            }.isNotEmpty()) {
            locationPerm.launcher.launch(locationPerms)
            if( locationPerm.sync.await() == false ) { // <- suspends
                has_access = false
            }
        }

        accessGranted.complete(has_access)
    }
}

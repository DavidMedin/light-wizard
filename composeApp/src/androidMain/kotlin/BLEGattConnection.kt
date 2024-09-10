import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import com.davidmedin.lightwizard.MainActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BLEGattConnection(private val activity : MainActivity, val device : BluetoothDevice) {


    @SuppressLint("MissingPermission")
    val gatt : BluetoothGatt = device.connectGatt(activity.applicationContext,false, GATTCallbacks)

    @SuppressLint("MissingPermission")
    @Composable
    fun getDeviceGatt(device : BluetoothDevice) : BluetoothGatt {
        return device.connectGatt(activity.applicationContext,false, GATTCallbacks)

    }

}
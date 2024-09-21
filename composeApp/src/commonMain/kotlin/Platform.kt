import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

interface Platform {
    val name: String
    val hasBluetooth: Boolean
    val state: String
    fun bluetoothEnabled(): Boolean

    @Composable
    fun doBluetoothThings()

    @Composable
    fun hasPermissions() : State<Boolean>
    suspend fun requestPermissions()
}

//expect fun getPlatform(): Platform

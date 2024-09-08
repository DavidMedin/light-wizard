import androidx.compose.runtime.Composable

interface Platform {
    val name: String
    val hasBluetooth: Boolean
    val state: String
    fun bluetoothEnabled(): Boolean

    @Composable
    fun doBluetoothThings()

    @Composable
    fun requestPermissions()
}

//expect fun getPlatform(): Platform

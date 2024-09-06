interface Platform {
    val name: String
    val hasBluetooth: Boolean
    val state: String
    fun bluetoothEnabled(): Boolean
    fun doBluetoothThings()
    fun requestPermissions()
}

//expect fun getPlatform(): Platform

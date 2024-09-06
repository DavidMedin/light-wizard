class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val hasBluetooth: Boolean = false
    override fun getAddress(): String {
        return "Device has no Bluetooth address."
    }

    override fun bluetoothEnabled(): Boolean? {
        return false
    }
}

//actual fun getPlatform(): Platform = JVMPlatform()
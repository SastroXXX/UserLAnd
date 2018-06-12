package tech.ula.utils

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import tech.ula.R
import java.io.File
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class DownloadUtility(val uiContext: Context) {

    private val downloadManager: DownloadManager by lazy {
        uiContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    companion object {
        val CONTINUE = 0
        val TURN_ON_WIFI = 1
        val CANCEL = 2
    }

    // Prefix file name with OS type to move it into the correct folder
    val assets = arrayListOf(
            "support:proot" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/mainSupport/arm/proot",
            "support:busybox" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/mainSupport/arm/busybox",
            "support:libtalloc.so.2" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/mainSupport/arm/libtalloc.so.2",
            "support:execInProot.sh" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/mainSupport/main/execInProot.sh",
            "support:killProcTree.sh" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/mainSupport/main/killProcTree.sh",
            "support:isServerInProcTree.sh" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/mainSupport/main/isServerInProcTree.sh"
    )

    val debianAssets = listOf(
            "debian:startSSHServer.sh" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/debianSupport/main/startSSHServer.sh",
            "debian:extractFilesystem.sh" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/debianSupport/main/extractFilesystem.sh",
            "debian:busybox" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/debianSupport/arm/busybox",
            "debian:libdisableselinux.so" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/debianSupport/arm/libdisableselinux.so",
            "debian:ld.so.preload" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/debianSupport/main/ld.so.preload",
            "debian:rootfs.tar.gz" to "https://s3-us-west-2.amazonaws.com/tech.ula.us.west.oregon/debianSupport/arm/rootfs.tar.gz"
    )

    fun addRequirements(filesystemType: String) {
        when(filesystemType) {
            "debian" -> assets.addAll(debianAssets)
            else -> return
        }
    }

    fun checkIfLargeRequirement(): Boolean {
        if(!isWifiEnabled()) {
            assets.forEach {
                (type, _) ->
                if(type.contains("rootfs") && assetNeedsToUpdated(type)) {
                    return true
                }
            }
        }
        return false
    }

    suspend fun displayWifiChoices(): Int {
        lateinit var result: Continuation<Int>
        val builder = AlertDialog.Builder(uiContext)
        builder.setMessage(R.string.alert_wifi_disabled_message)
                .setTitle(R.string.alert_wifi_disabled_title)
                .setPositiveButton(R.string.alert_wifi_disabled_force_button, {
                    dialog, _ ->
                    dialog.dismiss()
                    result.resume(CONTINUE)
                })
                .setNegativeButton(R.string.alert_wifi_disabled_turn_on_wifi_button, {
                    dialog, _ ->
                    dialog.dismiss()
                    result.resume(TURN_ON_WIFI)
                })
                .setNeutralButton(R.string.alert_wifi_disabled_cancel_button, {
                    dialog, _ ->
                    dialog.dismiss()
                    result.resume(CANCEL)
                })
                .create()
                .show()

        return suspendCoroutine { continuation -> result = continuation }
    }

    private fun download(type: String, url: String): Long {
        // TODO Dynamically adjust allowed network types to ensure no mobile use
        // Currently just assuming the dialog choices succeed in some way
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setAllowedOverMetered(false)
        request.setAllowedOverRoaming(false)
        request.setDescription("Downloading $type.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "UserLAnd:$type")

        return downloadManager.enqueue(request)
    }

    private fun assetNeedsToUpdated(type: String): Boolean {
        val (subdirectory, filename) = type.split(":")
        val asset = File("${uiContext.filesDir.path}/$subdirectory/$filename")
        // TODO more sophisticated version checking
        return !asset.exists()
    }

    fun downloadRequirements(): List<Long> {
        return assets
                .filter {
                    (type, _) ->
                    assetNeedsToUpdated(type)
                }
                .map {
                    (type, endpoint) ->
                    download(type, endpoint)
                }
    }

    private fun isWifiEnabled(): Boolean {
        val connectivityManager = uiContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI
    }
}

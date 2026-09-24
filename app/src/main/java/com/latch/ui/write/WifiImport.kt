package com.latch.ui.write

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.latch.nfc.WifiQr

data class NearbyNetwork(val ssid: String, val caps: String, val level: Int, val current: Boolean)

/** Lists nearby Wi-Fi networks. Needs location permission and location turned on (Android rule, see manifest). */
@Composable
fun NetworkPicker(onDismiss: () -> Unit, onPick: (NearbyNetwork) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocation(context)) }
    var networks by remember { mutableStateOf<List<NearbyNetwork>?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasLocation(context) }
    val locationOn = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true

    if (granted && locationOn) {
        DisposableEffect(Unit) {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) { networks = readNetworks(context) }
            }
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            networks = readNetworks(context)
            @Suppress("DEPRECATION") runCatching { wifi?.startScan() } // throttled by Android, so cached results show first
            onDispose { context.unregisterReceiver(receiver) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
        title = { Text("Nearby networks") },
        text = {
            when {
                !granted -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Android only shows Wi-Fi network names to apps that have location permission. Latch uses it for this list and nothing else.")
                    Button(onClick = { ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                        Text("Allow")
                    }
                }
                !locationOn -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Turn on Location so Android can list nearby networks.")
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Open settings") }
                }
                networks == null -> CircularProgressIndicator()
                networks!!.isEmpty() -> Text("No networks found yet. Give it a few seconds, or move closer to the router.")
                else -> Column {
                    Text(
                        "Android doesn't let apps read saved passwords, so you'll type it (or use Import QR code).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(networks!!, key = { it.ssid }) { n ->
                            Row(Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (WifiQr.classify(n.caps).first == com.latch.nfc.WifiSecurity.Open) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(n.ssid, style = MaterialTheme.typography.titleSmall)
                                    if (n.current) Text("Connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun hasLocation(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION")
private fun readNetworks(context: Context): List<NearbyNetwork> {
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return emptyList()
    val current = runCatching { wifi.connectionInfo?.ssid?.removeSurrounding("\"") }.getOrNull()
    return runCatching { wifi.scanResults }.getOrDefault(emptyList())
        .filter { !it.SSID.isNullOrBlank() }
        .groupBy { it.SSID }
        .map { (ssid, list) ->
            val best = list.maxBy { it.level }
            NearbyNetwork(ssid, best.capabilities.orEmpty(), best.level, ssid == current)
        }
        .sortedWith(compareByDescending<NearbyNetwork> { it.current }.thenByDescending { it.level })
}

/** Decodes a QR code from an image (a screenshot of Android's "QR code" share screen, or a photo of a router sticker). */
fun decodeQr(context: Context, uri: Uri): String? = runCatching {
    val src = ImageDecoder.createSource(context.contentResolver, uri)
    val full = ImageDecoder.decodeBitmap(src) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
    // Try full size first, then a downscaled copy. Very large photos sometimes decode better smaller.
    listOf(full, scaled(full, 1200)).firstNotNullOfOrNull { bmp -> runCatching { decode(bmp) }.getOrNull() }
}.getOrNull()

private fun decode(bmp: Bitmap): String {
    val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
    val source = RGBLuminanceSource(bmp.width, bmp.height, px)
    val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
    return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
}

private fun scaled(b: Bitmap, max: Int): Bitmap {
    val s = max.toFloat() / maxOf(b.width, b.height)
    return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
}

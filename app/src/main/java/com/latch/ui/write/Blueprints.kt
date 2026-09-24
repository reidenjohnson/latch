package com.latch.ui.write

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.latch.ui.theme.Accent
import com.latch.ui.theme.extras
import com.latch.nfc.Mirror
import com.latch.nfc.RecordSpec
import com.latch.nfc.RecordType
import com.latch.nfc.SocialPlatform
import com.latch.nfc.WifiSecurity

enum class BlueprintGroup(val label: String) {
    Home("Home"), Share("Share"), Safety("Safety"), Everyday("Everyday"), Power("Power moves");

    val accent: Color
        @Composable get() = when (this) {
            Home -> Accent.fern
            Share, Everyday -> Accent.teal
            Safety, Power -> Accent.amber
        }

    val gradient: List<Color>
        @Composable get() = when (this) {
            Home -> extras.fernGradient
            Share, Everyday -> extras.tealGradient
            Safety, Power -> extras.amberGradient
        }
}

/**
 * Ready-made tag ideas. Each one pre-fills the composer. If nothing personal is needed, it goes straight to the scan.
 * [how] explains what happens on tap. It only states behavior that's standard Android (see Payloads/Records sources).
 */
data class Blueprint(
    val title: String,
    val pitch: String,
    val how: String,
    val group: BlueprintGroup,
    val icon: ImageVector,
    val specs: List<RecordSpec>,
)

val blueprints = listOf(
    Blueprint(
        "Guest Wi-Fi", "Stick it by the door. Guests tap to join, so nobody reads a password out loud.",
        "Android offers to connect when the tag is tapped.", BlueprintGroup.Home, Icons.Rounded.Wifi,
        listOf(RecordSpec(RecordType.WiFi, mapOf("security" to WifiSecurity.Wpa2.name))),
    ),
    Blueprint(
        "Digital business card", "Tap your phone case or keychain to hand over your contact info.",
        "Stored as a standard vCard contact.", BlueprintGroup.Share, Icons.Rounded.Badge,
        listOf(RecordSpec(RecordType.Contact)),
    ),
    Blueprint(
        "Follow me", "Put your Instagram (or TikTok, X, YouTube…) on a sticker.",
        "Opens your profile in their browser or app.", BlueprintGroup.Share, Icons.Rounded.ThumbUp,
        listOf(RecordSpec(RecordType.Social, mapOf("platform" to SocialPlatform.Instagram.name))),
    ),
    Blueprint(
        "Tip jar", "A tap-to-pay link for your Venmo or PayPal.",
        "Opens your payment page. They choose the amount.", BlueprintGroup.Share, Icons.Rounded.Savings,
        listOf(RecordSpec(RecordType.Social, mapOf("platform" to SocialPlatform.Venmo.name))),
    ),
    Blueprint(
        "Leave a review", "Put your Google review or menu link on the counter.",
        "Opens the link in their browser.", BlueprintGroup.Share, Icons.Rounded.StarRate,
        listOf(RecordSpec(RecordType.Link)),
    ),
    Blueprint(
        "Event photo drop", "Guests tap to open the shared album for a wedding or party.",
        "Opens the album link in their browser.", BlueprintGroup.Share, Icons.Rounded.PhotoLibrary,
        listOf(RecordSpec(RecordType.Link)),
    ),
    Blueprint(
        "Lost & found", "Stick it on your keys, wallet or bag. Whoever finds it can call you.",
        "Opens their dialer with your number filled in. Latch and NFC reader apps also show your note.",
        BlueprintGroup.Safety, Icons.Rounded.Search,
        listOf(
            RecordSpec(RecordType.Phone),
            RecordSpec(RecordType.Text, mapOf("text" to "If you found this, thank you! Please call or text me.")),
        ),
    ),
    Blueprint(
        "Pet tag", "Clip it to a collar. Anyone who finds your pet can call you right away.",
        "Opens their dialer with your number filled in.", BlueprintGroup.Safety, Icons.Rounded.Pets,
        listOf(
            RecordSpec(RecordType.Phone),
            RecordSpec(RecordType.Text, mapOf("text" to "Hi! I'm lost. Please call my family.")),
        ),
    ),
    Blueprint(
        "Medical ID", "Blood type, allergies and an emergency contact on your keychain or phone case.",
        "Any NFC reader app shows the info. If you add a contact phone, a second record can open the dialer.",
        BlueprintGroup.Safety, Icons.Rounded.MedicalServices,
        listOf(RecordSpec(RecordType.Emergency)),
    ),
    Blueprint(
        "Navigate home", "Stick it on the dash. Tap to start directions home.",
        "Opens Google Maps directions to the address.", BlueprintGroup.Everyday, Icons.Rounded.Navigation,
        listOf(RecordSpec(RecordType.Location, mapOf("mode" to "directions"))),
    ),
    Blueprint(
        "Car mode", "Tap the dash to open your music or maps app.",
        "Launches the app you pick.", BlueprintGroup.Everyday, Icons.Rounded.DirectionsCar,
        listOf(RecordSpec(RecordType.App)),
    ),
    Blueprint(
        "\"I'm home\" text", "Tap by the door to text someone that you made it home.",
        "Opens a pre-written text. Nothing sends until you press send.", BlueprintGroup.Everyday, Icons.Rounded.Home,
        listOf(RecordSpec(RecordType.Sms, mapOf("body" to "Made it home!"))),
    ),
    Blueprint(
        "Plant care card", "Care notes that live in the pot.",
        "Latch and NFC reader apps show the note.", BlueprintGroup.Home, Icons.Rounded.LocalFlorist,
        listOf(RecordSpec(RecordType.Text, mapOf("text" to "Water: every Sunday\nLight: bright, indirect\nFeed: monthly in spring & summer"))),
    ),
    Blueprint(
        "Pair a speaker", "Tap to start pairing a Bluetooth speaker or headphones.",
        "Android offers to pair with the device.", BlueprintGroup.Home, Icons.Rounded.Speaker,
        listOf(RecordSpec(RecordType.Bluetooth)),
    ),
    Blueprint(
        "Secret card", "Keep a code or combination on a card that only opens with your password.",
        "Encrypted with AES-256. Only Latch can unlock it.", BlueprintGroup.Power, Icons.Rounded.EnhancedEncryption,
        listOf(RecordSpec(RecordType.Secret)),
    ),
    Blueprint(
        "Tap counter link", "A link that includes how many times the tag has been scanned. Great for tracking a flyer.",
        "The tag's chip fills in the live count on every scan. Needs an NTAG213/215/216.",
        BlueprintGroup.Power, Icons.Rounded.Insights,
        listOf(RecordSpec(RecordType.LiveLink, mapOf("mirror" to Mirror.Count.name))),
    ),
)

package com.latch.ui.write

import android.nfc.NdefMessage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import com.latch.nfc.Payloads
import com.latch.nfc.WifiSecurity
import com.latch.nfc.WifiTlv

enum class WriteKind(val title: String, val blurb: String, val icon: ImageVector) {
    Link("Link", "Open a website", Icons.Rounded.Link),
    Note("Text", "Show a note", Icons.AutoMirrored.Rounded.Notes),
    WiFi("Wi-Fi", "Join a network", Icons.Rounded.Wifi),
    Contact("Contact", "Share your details", Icons.Rounded.ContactPage),
    Phone("Call", "Start a phone call", Icons.Rounded.Call),
    Sms("Message", "Draft a text", Icons.Rounded.Sms),
    Email("Email", "Draft an email", Icons.Rounded.Email),
    Location("Place", "Open a map", Icons.Rounded.Place),
    App("App", "Launch an app", Icons.Rounded.Apps),
}

data class FieldSpec(
    val key: String,
    val label: String,
    val hint: String = "",
    val keyboard: KeyboardType = KeyboardType.Text,
    val required: Boolean = true,
    val multiline: Boolean = false,
    val secret: Boolean = false,
)

val WriteKind.fields: List<FieldSpec>
    get() = when (this) {
        WriteKind.Link -> listOf(FieldSpec("url", "Website", "example.com", KeyboardType.Uri))
        WriteKind.Note -> listOf(FieldSpec("text", "Text", "Anything you like", multiline = true))
        WriteKind.WiFi -> listOf(
            FieldSpec("ssid", "Network name", "Exactly as it appears in Wi-Fi settings"),
            FieldSpec("password", "Password", keyboard = KeyboardType.Password, secret = true),
        )
        WriteKind.Contact -> listOf(
            FieldSpec("name", "Name"),
            FieldSpec("phone", "Phone", keyboard = KeyboardType.Phone, required = false),
            FieldSpec("email", "Email", keyboard = KeyboardType.Email, required = false),
            FieldSpec("org", "Company", required = false),
            FieldSpec("website", "Website", keyboard = KeyboardType.Uri, required = false),
        )
        WriteKind.Phone -> listOf(FieldSpec("number", "Phone number", keyboard = KeyboardType.Phone))
        WriteKind.Sms -> listOf(
            FieldSpec("number", "Phone number", keyboard = KeyboardType.Phone),
            FieldSpec("body", "Message", "Optional. Pre-fills the text", required = false, multiline = true),
        )
        WriteKind.Email -> listOf(
            FieldSpec("to", "To", "name@example.com", KeyboardType.Email),
            FieldSpec("subject", "Subject", required = false),
            FieldSpec("body", "Message", required = false, multiline = true),
        )
        WriteKind.Location -> listOf(FieldSpec("place", "Place or coordinates", "Central Park, or 40.7829, -73.9654"))
        WriteKind.App -> emptyList()
    }

/** What happens when someone taps the tag. Only states behavior Android documents or that we verified. */
val WriteKind.tip: String?
    get() = when (this) {
        WriteKind.Link -> "Opens in the phone's browser. The other phone doesn't need any app."
        WriteKind.WiFi -> "Android offers to connect when the tag is tapped. The password is stored as plain text, " +
            "so anyone with an NFC app can read it. Android's tag reader has no WPA3-only option. Pick WPA2/WPA3 " +
            "for WPA2 or mixed-mode routers."
        WriteKind.Contact -> "Saved as a standard vCard contact card."
        WriteKind.Note -> "Plain text. Phones usually need an NFC app, like Latch, to show it."
        WriteKind.Location -> "Opens in the phone's map app."
        WriteKind.Sms, WriteKind.Email -> "Opens a draft. Nothing is sent until they press send."
        WriteKind.Phone -> "Opens the dialer with the number filled in. Nothing is called until they press call."
        WriteKind.App -> null
    }

sealed interface Validation {
    data class Ok(val message: NdefMessage) : Validation
    data class Invalid(val fieldErrors: Map<String, String>) : Validation
}

fun WriteKind.build(values: Map<String, String>, security: WifiSecurity): Validation {
    fun v(key: String) = values[key].orEmpty().trim()
    val errors = mutableMapOf<String, String>()
    fields.filter { it.required && v(it.key).isEmpty() }.forEach { errors[it.key] = "" } // empty = required, no message yet

    when (this) {
        WriteKind.Link -> if (v("url").isNotEmpty() && !v("url").contains('.') && !v("url").contains(':')) {
            errors["url"] = "That doesn't look like a web address"
        }
        WriteKind.WiFi -> {
            val pw = values["password"].orEmpty()
            if (security == WifiSecurity.Open) errors.remove("password")
            else if (pw.isNotEmpty() && pw.length < 8) errors["password"] = "WPA passwords are at least 8 characters"
            else if (pw.toByteArray().size > WifiTlv.MAX_KEY_BYTES) errors["password"] = "Too long (max 64 characters)"
        }
        WriteKind.Email -> if (v("to").isNotEmpty() && !v("to").contains('@')) errors["to"] = "Needs an @"
        WriteKind.Phone, WriteKind.Sms -> if (v("number").isNotEmpty() && v("number").none { it.isDigit() }) {
            errors["number"] = "Needs at least one digit"
        }
        else -> Unit
    }
    if (errors.isNotEmpty()) return Validation.Invalid(errors)

    val message = when (this) {
        WriteKind.Link -> Payloads.link(v("url"))
        WriteKind.Note -> Payloads.text(values["text"].orEmpty().trim())
        WriteKind.WiFi -> Payloads.wifi(values["ssid"].orEmpty(), security, values["password"].orEmpty())
        WriteKind.Contact -> Payloads.contact(v("name"), v("phone"), v("email"), v("org"), v("website"))
        WriteKind.Phone -> Payloads.phone(v("number"))
        WriteKind.Sms -> Payloads.sms(v("number"), values["body"].orEmpty().trim())
        WriteKind.Email -> Payloads.email(v("to"), v("subject"), values["body"].orEmpty().trim())
        WriteKind.Location -> Payloads.location(v("place"))
        WriteKind.App -> return Validation.Invalid(emptyMap())
    }
    return Validation.Ok(message)
}

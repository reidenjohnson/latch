package com.latch.nfc

import android.content.Context
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import org.json.JSONArray
import org.json.JSONObject

/** Everything Latch can write. UI details (icons) live in the UI layer. */
enum class RecordType(val title: String, val blurb: String) {
    Link("Link", "Open a website"),
    Social("Social & pay", "Profile or payment link"),
    Text("Text", "Show a note"),
    WiFi("Wi-Fi", "Join a network"),
    Contact("Contact", "Share your details"),
    Phone("Call", "Start a phone call"),
    Sms("Message", "Draft a text"),
    Email("Email", "Draft an email"),
    Location("Place", "Open a map or directions"),
    App("App", "Launch an app"),
    Bluetooth("Bluetooth", "Pair a device"),
    Emergency("Emergency info", "Medical info + ICE contact"),
    Secret("Locked note", "Password-encrypted text"),
    LiveLink("Live link", "Link with scan count / tag ID"),
    Custom("Custom record", "Any MIME or external type"),
}

enum class Input { Text, Multiline, Url, Phone, Email, Password, Number }

data class Field(
    val key: String,
    val label: String,
    val hint: String = "",
    val input: Input = Input.Text,
    val required: Boolean = true,
    /** Chip choices as value → label. The first is the default. */
    val options: List<Pair<String, String>>? = null,
    /** Only shown when another field has one of these values. */
    val showIf: Pair<String, Set<String>>? = null,
)

/** A record the user is building: its type plus raw field values. Placeholders like {n} are filled in at write time. */
data class RecordSpec(val type: RecordType, val values: Map<String, String> = emptyMap()) {
    fun value(key: String): String = values[key] ?: Records.fields(type).firstOrNull { it.key == key }?.options?.first()?.first.orEmpty()

    fun toJson(): JSONObject = JSONObject().put("type", type.name).put("values", JSONObject(values))

    companion object {
        fun fromJson(o: JSONObject): RecordSpec? {
            val type = runCatching { RecordType.valueOf(o.getString("type")) }.getOrNull() ?: return null
            val v = o.optJSONObject("values") ?: JSONObject()
            return RecordSpec(type, v.keys().asSequence().associateWith { v.getString(it) })
        }
        fun listToJson(list: List<RecordSpec>) = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()
        fun listFromJson(s: String): List<RecordSpec> = runCatching {
            val a = JSONArray(s); (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

/**
 * Profile URL formats. Each was checked to resolve (HTTP 200/3xx) with a known public account on 2026-09-24.
 * Cash App was left out because its format couldn't be confirmed.
 */
enum class SocialPlatform(val label: String, val pattern: String, val handleHint: String) {
    Instagram("Instagram", "https://www.instagram.com/%s", "username"),
    TikTok("TikTok", "https://www.tiktok.com/@%s", "username"),
    X("X", "https://x.com/%s", "username"),
    YouTube("YouTube", "https://www.youtube.com/@%s", "handle"),
    Facebook("Facebook", "https://www.facebook.com/%s", "username"),
    LinkedIn("LinkedIn", "https://www.linkedin.com/in/%s", "profile id"),
    Snapchat("Snapchat", "https://www.snapchat.com/add/%s", "username"),
    Threads("Threads", "https://www.threads.com/@%s", "username"),
    GitHub("GitHub", "https://github.com/%s", "username"),
    Twitch("Twitch", "https://www.twitch.tv/%s", "username"),
    WhatsApp("WhatsApp", "https://wa.me/%s", "phone with country code"),
    Venmo("Venmo", "https://venmo.com/u/%s", "username"),
    PayPal("PayPal", "https://paypal.me/%s", "paypal.me name"),
    ;

    fun url(handle: String): String {
        val h = handle.trim().removePrefix("@")
        val clean = if (this == WhatsApp) h.filter { it.isDigit() } else Uri.encode(h)
        return pattern.format(clean)
    }
}

object Records {

    private val wifiSecurity = listOf(WifiSecurity.Wpa2.name to WifiSecurity.Wpa2.label, WifiSecurity.Open.name to WifiSecurity.Open.label)

    fun fields(type: RecordType): List<Field> = when (type) {
        RecordType.Link -> listOf(Field("url", "Website", "example.com", Input.Url))
        RecordType.Social -> listOf(
            Field("platform", "Platform", options = SocialPlatform.entries.map { it.name to it.label }),
            Field("handle", "Username", "yourname"),
        )
        RecordType.Text -> listOf(Field("text", "Text", "Anything you like", Input.Multiline))
        RecordType.WiFi -> listOf(
            Field("security", "Security", options = wifiSecurity),
            Field("ssid", "Network name", "Exactly as it appears in Wi-Fi settings"),
            Field("password", "Password", input = Input.Password, showIf = "security" to setOf(WifiSecurity.Wpa2.name)),
        )
        RecordType.Contact -> listOf(
            Field("name", "Name"),
            Field("phone", "Phone", input = Input.Phone, required = false),
            Field("email", "Email", input = Input.Email, required = false),
            Field("org", "Company", required = false),
            Field("website", "Website", input = Input.Url, required = false),
        )
        RecordType.Phone -> listOf(Field("number", "Phone number", input = Input.Phone))
        RecordType.Sms -> listOf(
            Field("number", "Phone number", input = Input.Phone),
            Field("body", "Message", "Optional. Pre-fills the text", Input.Multiline, required = false),
        )
        RecordType.Email -> listOf(
            Field("to", "To", "name@example.com", Input.Email),
            Field("subject", "Subject", required = false),
            Field("body", "Message", input = Input.Multiline, required = false),
        )
        RecordType.Location -> listOf(
            Field("mode", "Open as", options = listOf("map" to "Map pin", "directions" to "Directions (Google Maps)")),
            Field("place", "Place or coordinates", "Central Park, or 40.7829, -73.9654"),
        )
        RecordType.App -> listOf(Field("package", "App"))
        RecordType.Bluetooth -> listOf(
            Field("mac", "Device address", "AA:BB:CC:DD:EE:FF"),
            Field("name", "Device name", "Optional. Shown when pairing", required = false),
        )
        RecordType.Emergency -> listOf(
            Field("name", "Name"),
            Field("contactName", "Emergency contact", required = false),
            Field("contactPhone", "Contact phone", input = Input.Phone, required = false),
            Field("blood", "Blood type", required = false),
            Field("allergies", "Allergies", required = false, input = Input.Multiline),
            Field("meds", "Medications", required = false, input = Input.Multiline),
            Field("conditions", "Conditions / notes", required = false, input = Input.Multiline),
        )
        RecordType.Secret -> listOf(
            Field("text", "Secret text", input = Input.Multiline),
            Field("password", "Password", "At least 6 characters", Input.Password),
        )
        RecordType.LiveLink -> listOf(
            Field("mirror", "Add to the link", options = Mirror.entries.map { it.name to it.label }),
            Field("url", "Website", "example.com/tap", Input.Url),
            Field("param", "Parameter name", "tap", required = false),
        )
        RecordType.Custom -> listOf(
            Field("format", "Record type", options = listOf("mime" to "MIME type", "external" to "External type")),
            Field("recordType", "Type", "text/plain  or  example.com:mytype"),
            Field("encoding", "Payload is", options = listOf("text" to "Text", "hex" to "Hex bytes")),
            Field("payload", "Payload", input = Input.Multiline, required = false),
        )
    }

    fun visibleFields(spec: RecordSpec) = fields(spec.type).filter { f ->
        f.showIf == null || spec.value(f.showIf.first) in f.showIf.second
    }

    /** Field key → message. An empty message means "required". An empty map means valid. */
    fun validate(spec: RecordSpec): Map<String, String> {
        val v = { k: String -> spec.value(k).trim() }
        val errors = mutableMapOf<String, String>()
        visibleFields(spec).filter { it.required && it.options == null && v(it.key).isEmpty() }.forEach { errors[it.key] = "" }
        when (spec.type) {
            RecordType.Link, RecordType.LiveLink -> if (v("url").isNotEmpty() && '.' !in v("url") && ':' !in v("url")) {
                errors["url"] = "That doesn't look like a web address"
            }
            RecordType.WiFi -> {
                val pw = spec.value("password")
                if (spec.value("security") == WifiSecurity.Wpa2.name) {
                    if (pw.isNotEmpty() && pw.length < 8) errors["password"] = "WPA passwords are at least 8 characters"
                    else if (pw.toByteArray().size > WifiTlv.MAX_KEY_BYTES) errors["password"] = "Too long (max 64 characters)"
                }
            }
            RecordType.Email -> if (v("to").isNotEmpty() && '@' !in v("to")) errors["to"] = "Needs an @"
            RecordType.Phone, RecordType.Sms -> if (v("number").isNotEmpty() && v("number").none { it.isDigit() }) {
                errors["number"] = "Needs at least one digit"
            }
            RecordType.Bluetooth -> if (v("mac").isNotEmpty() && !Bluetooth.MAC.matches(v("mac"))) {
                errors["mac"] = "Format: AA:BB:CC:DD:EE:FF"
            }
            RecordType.Secret -> if (spec.value("password").isNotEmpty() && spec.value("password").length < 6) {
                errors["password"] = "At least 6 characters"
            }
            RecordType.Custom -> {
                val t = v("recordType")
                if (t.isNotEmpty() && spec.value("format") == "mime" && '/' !in t) errors["recordType"] = "MIME types look like type/subtype"
                if (t.isNotEmpty() && spec.value("format") == "external" && ':' !in t) errors["recordType"] = "External types look like domain.com:type"
                if (spec.value("encoding") == "hex" && hexOrNull(spec.value("payload")) == null) errors["payload"] = "Use hex pairs like 0A 1B 2C"
            }
            else -> Unit
        }
        return errors
    }

    /** Replaces {n} and {Column} placeholders in every value. */
    fun substitute(spec: RecordSpec, vars: Map<String, String>): RecordSpec {
        if (vars.isEmpty()) return spec
        return spec.copy(values = spec.values.mapValues { (_, s) ->
            vars.entries.fold(s) { acc, (k, v) -> acc.replace("{$k}", v) }
        })
    }

    fun mirrorOf(specs: List<RecordSpec>): Mirror? =
        specs.firstOrNull { it.type == RecordType.LiveLink }?.let { runCatching { Mirror.valueOf(it.value("mirror")) }.getOrNull() }

    /**
     * Builds NDEF records. [estimateOnly] uses 1 PBKDF2 iteration for locked notes, which gives the same size without
     * the slow key derivation (for the live size meter). Never write an estimate to a tag.
     */
    fun build(spec: RecordSpec, estimateOnly: Boolean = false): List<NdefRecord> {
        val v = { k: String -> spec.value(k).trim() }
        return when (spec.type) {
            RecordType.Link -> Payloads.link(v("url")).records.toList()
            RecordType.Social -> {
                val p = runCatching { SocialPlatform.valueOf(spec.value("platform")) }.getOrDefault(SocialPlatform.Instagram)
                listOf(NdefRecord.createUri(p.url(v("handle"))))
            }
            RecordType.Text -> listOf(NdefRecord.createTextRecord("en", spec.value("text").trim()))
            RecordType.WiFi -> {
                val sec = runCatching { WifiSecurity.valueOf(spec.value("security")) }.getOrDefault(WifiSecurity.Wpa2)
                Payloads.wifi(spec.value("ssid"), sec, spec.value("password")).records.toList()
            }
            RecordType.Contact -> Payloads.contact(v("name"), v("phone"), v("email"), v("org"), v("website")).records.toList()
            RecordType.Phone -> Payloads.phone(v("number")).records.toList()
            RecordType.Sms -> Payloads.sms(v("number"), spec.value("body").trim()).records.toList()
            RecordType.Email -> Payloads.email(v("to"), v("subject"), spec.value("body").trim()).records.toList()
            RecordType.Location -> if (spec.value("mode") == "directions") {
                // Google Maps URLs API: https://developers.google.com/maps/documentation/urls/get-started
                listOf(NdefRecord.createUri("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(v("place"))}"))
            } else Payloads.location(v("place")).records.toList()
            RecordType.App -> Payloads.app(v("package")).records.toList()
            RecordType.Bluetooth -> listOf(
                NdefRecord.createMime(Bluetooth.MIME_TYPE, Bluetooth.encode(v("mac"), v("name"))),
            )
            RecordType.Emergency -> {
                val text = buildString {
                    appendLine("EMERGENCY INFO")
                    appendLine("Name: ${v("name")}")
                    if (v("blood").isNotEmpty()) appendLine("Blood type: ${v("blood")}")
                    if (v("allergies").isNotEmpty()) appendLine("Allergies: ${v("allergies")}")
                    if (v("meds").isNotEmpty()) appendLine("Medications: ${v("meds")}")
                    if (v("conditions").isNotEmpty()) appendLine("Notes: ${v("conditions")}")
                    if (v("contactName").isNotEmpty() || v("contactPhone").isNotEmpty()) {
                        appendLine("Emergency contact: ${listOf(v("contactName"), v("contactPhone")).filter { it.isNotEmpty() }.joinToString(", ")}")
                    }
                }.trim()
                listOfNotNull(
                    NdefRecord.createTextRecord("en", text),
                    v("contactPhone").takeIf { it.isNotEmpty() }?.let { Payloads.phone(it).records.first() },
                )
            }
            RecordType.Secret -> {
                val iterations = if (estimateOnly) 1 else SecretBox.DEFAULT_ITERATIONS
                val sealed = SecretBox.seal(spec.value("text").trim(), spec.value("password"), iterations)
                listOf(NdefRecord.createExternal("latch.app", "secret", sealed))
            }
            RecordType.LiveLink -> {
                val mirror = runCatching { Mirror.valueOf(spec.value("mirror")) }.getOrDefault(Mirror.Count)
                val base = Payloads.link(v("url")).records.first().toUri().toString()
                val param = v("param").ifEmpty { "tap" }
                val sep = if ('?' in base) "&" else "?"
                listOf(NdefRecord.createUri("$base$sep$param=${mirror.placeholder}"))
            }
            RecordType.Custom -> {
                val payload = if (spec.value("encoding") == "hex") hexOrNull(spec.value("payload")) ?: ByteArray(0)
                else spec.value("payload").toByteArray(Charsets.UTF_8)
                val t = v("recordType")
                if (spec.value("format") == "external") {
                    listOf(NdefRecord.createExternal(t.substringBefore(':'), t.substringAfter(':'), payload))
                } else listOf(NdefRecord.createMime(t, payload))
            }
        }
    }

    fun message(specs: List<RecordSpec>, estimateOnly: Boolean = false): NdefMessage =
        NdefMessage(specs.flatMap { build(it, estimateOnly) }.toTypedArray())

    fun describe(spec: RecordSpec): String {
        val main = when (spec.type) {
            RecordType.Link, RecordType.LiveLink -> spec.value("url")
            RecordType.Social -> "${runCatching { SocialPlatform.valueOf(spec.value("platform")).label }.getOrDefault("")} @${spec.value("handle")}"
            RecordType.Text -> spec.value("text")
            RecordType.WiFi -> spec.value("ssid")
            RecordType.Contact, RecordType.Emergency -> spec.value("name")
            RecordType.Phone, RecordType.Sms -> spec.value("number")
            RecordType.Email -> spec.value("to")
            RecordType.Location -> spec.value("place")
            RecordType.App -> spec.value("label").ifEmpty { spec.value("package") }
            RecordType.Bluetooth -> spec.value("name").ifEmpty { spec.value("mac") }
            RecordType.Secret -> "Encrypted"
            RecordType.Custom -> spec.value("recordType")
        }
        return "${spec.type.title} · ${main.lineSequence().firstOrNull().orEmpty().take(60)}"
    }

    fun hexOrNull(s: String): ByteArray? {
        val clean = s.filter { !it.isWhitespace() && it != ':' && it != ',' }
        if (clean.length % 2 != 0 || clean.any { it !in "0123456789abcdefABCDEF" }) return null
        return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /** Turns a tag's records back into editable specs ("Edit" on the Read tab). Unknown records become Custom (hex). */
    fun import(message: NdefMessage, context: Context): List<RecordSpec> = message.records.mapNotNull { r ->
        runCatching { importRecord(r, context) }.getOrNull()
    }

    private fun importRecord(r: NdefRecord, context: Context): RecordSpec? {
        val parsed = NdefParser.parse(r, context)
        val d = parsed.details.toMap()
        return when (parsed.kind) {
            RecordKind.Empty -> null
            RecordKind.Link -> RecordSpec(RecordType.Link, mapOf("url" to parsed.value))
            RecordKind.Text -> RecordSpec(RecordType.Text, mapOf("text" to parsed.value))
            RecordKind.Phone -> RecordSpec(RecordType.Phone, mapOf("number" to parsed.value))
            RecordKind.Sms -> RecordSpec(RecordType.Sms, mapOf("number" to parsed.value, "body" to d["Message"].orEmpty()))
            RecordKind.Email -> RecordSpec(RecordType.Email, mapOf("to" to parsed.value, "subject" to d["Subject"].orEmpty(), "body" to d["Message"].orEmpty()))
            RecordKind.Location -> RecordSpec(RecordType.Location, mapOf("mode" to "map", "place" to parsed.value))
            RecordKind.App -> RecordSpec(RecordType.App, mapOf("package" to parsed.appPackage.orEmpty(), "label" to parsed.value))
            RecordKind.Contact -> RecordSpec(RecordType.Contact, mapOf(
                "name" to parsed.value, "phone" to d["Phone"].orEmpty(), "email" to d["Email"].orEmpty(),
                "org" to d["Company"].orEmpty(), "website" to d["Website"].orEmpty(),
            ))
            RecordKind.WiFi -> RecordSpec(RecordType.WiFi, mapOf(
                "ssid" to parsed.value, "password" to parsed.secret.orEmpty(),
                "security" to if (parsed.secret == null) WifiSecurity.Open.name else WifiSecurity.Wpa2.name,
            ))
            RecordKind.Bluetooth -> Bluetooth.decode(r.payload).let {
                RecordSpec(RecordType.Bluetooth, mapOf("mac" to it.mac, "name" to it.name.orEmpty()))
            }
            else -> {
                val type = String(r.type, Charsets.US_ASCII)
                RecordSpec(RecordType.Custom, mapOf(
                    "format" to if (r.tnf == NdefRecord.TNF_EXTERNAL_TYPE) "external" else "mime",
                    "recordType" to type.ifEmpty { "application/octet-stream" },
                    "encoding" to "hex",
                    "payload" to r.payload.joinToString(" ") { "%02X".format(it) },
                ))
            }
        }
    }
}

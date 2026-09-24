# Latch

A clean, fast NFC tag reader and writer for Android, with every feature free.

Most NFC apps on the Play Store bury simple actions behind subscriptions and cluttered screens. Latch keeps
it to four tabs, speaks plain English, and checks every write so you know the tag actually worked.

## Features

**Read.** Tap any tag to see what's on it, in words, not hex: websites, Wi-Fi logins, contacts, phone
numbers, map locations and app links. It also shows the exact chip (NTAG213/215/216), how much memory is
used and whether the tag is locked. Open, copy or share anything you find.

**Write.** Pick what the tag should do, fill in a field or two, and tap. You can stack several records on one tag.

| | |
|---|---|
| Link / Social & pay | Website, or a profile on Instagram, TikTok, X, YouTube, LinkedIn, Venmo, PayPal and more |
| Text | Stores a note |
| Wi-Fi | Offers to join your network (standard WSC format) |
| Contact | Shares a vCard |
| Call / Message / Email | Opens a pre-filled dialer, text or email draft |
| Place | Opens a map pin, or Google Maps directions |
| App | Launches any installed app |
| Bluetooth | Offers to pair a speaker or headset |
| Emergency info | Medical details plus an emergency contact |
| Locked note | Text encrypted with AES-256-GCM (PBKDF2, 600k rounds) that only opens with your password |
| Live link | A URL that the chip itself fills with its live scan count and/or ID on every tap |
| Custom | Any MIME or NFC Forum external record, as text or hex |

**Blueprints.** Ready-made ideas like guest Wi-Fi, lost & found, pet tag, medical ID, tip jar, navigate home
and tap-counter flyers. Tap one and fill in the blanks. If nothing personal is needed, it goes straight to the scan.

**Saved designs & editing.** Save any design to reuse it, or read a tag and tap *Edit & rewrite* to change it.

**Write many.** Write the same thing to a stack of tags, number them with `{n}`, or fill them row by row from a
CSV spreadsheet using `{Column}` placeholders. You can also lock each one as you go.

**Tools.** Copy, erase, lock forever, password-protect or unprotect (NTAG21x `PWD_AUTH`), turn the chip's scan
counter on or off, dump the full memory in hex, send raw commands (NfcA/B/F/V, ISO-DEP), and scan many tags into a CSV.

**Read extras.** Checks whether the chip is genuine using NXP's originality signature (ECDSA secp128r1).
Shows password status, the live scan count, ATQA/SAK and each record's raw bytes.

**History.** Everything you read or write is saved and can be exported as CSV or written again.

## Reliability

- **Reader mode.** While Latch is open, every tap goes straight to Latch instead of the system's "open with"
  dispatcher, so taps behave the same way every time.
- **Read-back verification.** After each write, Latch reads the tag again and compares it byte for byte.
  "Verified" only appears when they match.
- **Armed until done.** If a write fails because the tag moved, Latch explains why, keeps the write ready,
  and retries on the next tap.
- **Real chip detection.** The chip is identified with the NTAG `GET_VERSION` command
  ([NXP datasheet](https://www.nxp.com/docs/en/data-sheet/NTAG213_215_216.pdf), Tables 26 and 28), never
  guessed from the tag's size.
- **Wi-Fi tags match Android's own parser**
  ([source](https://android.googlesource.com/platform/packages/modules/Nfc/+/refs/heads/main/NfcNci/src/com/android/nfc/NfcWifiProtectedSetup.java)),
  including attribute order for older Android versions.

## Coming next: Focus

Tap your own Latch tag to lock distracting apps like Instagram and YouTube. The only way to unlock is to
tap the tag again, and Latch shows how long you stayed off. Blocking uses an Android Accessibility service
and only affects the apps you choose. There's no root and no system changes, and uninstalling Latch undoes
everything.

## Tech

Kotlin · Jetpack Compose · Material 3 · Android NFC (`NfcAdapter` reader mode, `Ndef`, `NdefFormatable`, `NfcA`).
No third-party dependencies beyond AndroidX. Min SDK 30 (Android 11).

```
app/src/main/java/com/latch/
  nfc/      NFC logic, no UI: controller, tag I/O, NDEF parser, payload builders
  data/     history store
  ui/       Compose screens (read, write, tools, history), components, theme
```

## Build

Requires JDK 17+ and the Android SDK (compileSdk 37).

```
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK
```

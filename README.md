# Latch

A clean, fast NFC tag reader and writer for Android, with every feature free.

Most NFC apps on the Play Store bury simple actions behind subscriptions and cluttered screens. Latch keeps
it to four tabs, speaks plain English, and checks every write so you know the tag actually worked.

## Features

**Read.** Tap any tag to see what's on it, in words, not hex: websites, Wi-Fi logins, contacts, phone
numbers, map locations and app links. It also shows the exact chip (NTAG213/215/216), how much memory is
used and whether the tag is locked. Open, copy or share anything you find.

**Write.** Pick what the tag should do, fill in a field or two, and tap:

| | |
|---|---|
| Link | Opens a website |
| Text | Stores a note |
| Wi-Fi | Offers to join your network (standard WSC format) |
| Contact | Shares a vCard |
| Call / Message / Email | Opens a pre-filled dialer, text or email draft |
| Place | Opens a map pin or search |
| App | Launches any installed app |

A live size meter tells you which tag sizes the data fits on before you write.

**Tools.** Copy one tag to another, erase a tag, or permanently lock one (with a confirmation step).

**History.** Everything you read or write is saved, so you can write the same thing to another tag later.

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

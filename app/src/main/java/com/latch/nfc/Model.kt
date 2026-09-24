package com.latch.nfc

import android.net.Uri
import android.nfc.NdefMessage

enum class NfcAvailability { On, Off, Unsupported }

/** A user-facing explanation of what went wrong and what to do about it. */
data class Problem(val title: String, val detail: String)

class NfcProblem(val problem: Problem) : Exception(problem.title)

/** Something the user asked to do to the *next* tag they tap. Stays armed until it succeeds or is cancelled. */
sealed interface Operation {
    data class Write(val message: NdefMessage, val label: String) : Operation
    data object Erase : Operation
    data object CopySource : Operation
    data class CopyTarget(val message: NdefMessage, val label: String, val sourceUid: String) : Operation
    data object MakeReadOnly : Operation
}

sealed interface SheetState {
    data object Hidden : SheetState
    /** Waiting for a tag. [problem] is set when the last attempt failed; the next tap retries. */
    data class Waiting(val op: Operation, val problem: Problem? = null) : SheetState
    data class Done(val op: Operation, val title: String, val detail: String?) : SheetState
}

enum class RecordKind { Link, Text, WiFi, Contact, Phone, Sms, Email, Location, App, Data, Empty, Unknown }

/** One NDEF record, translated into plain English. */
data class ParsedRecord(
    val kind: RecordKind,
    val label: String,
    val value: String,
    val details: List<Pair<String, String>> = emptyList(),
    val openUri: Uri? = null,
    val appPackage: String? = null,
    val copyText: String? = null,
    /** Shown masked with a reveal toggle (Wi-Fi passwords). */
    val secret: String? = null,
)

data class TagSnapshot(
    val uid: String,
    val typeLabel: String,
    /** Exact chip, only when the tag answered GET_VERSION (e.g. "NTAG215"). Null when unknown; never guessed. */
    val chip: String?,
    val techs: List<String>,
    /** Bytes available for NDEF data, as reported by the tag. Null for tags that aren't NDEF-formatted yet. */
    val capacity: Int?,
    val used: Int,
    val writable: Boolean?,
    val canLock: Boolean,
    /** True when Latch can read/write this tag (NDEF or NDEF-formatable). False for bank/transit/access cards. */
    val supported: Boolean,
    val message: NdefMessage?,
    val records: List<ParsedRecord>,
    val summary: String,
    val time: Long,
) {
    val isBlank: Boolean get() = supported && (message == null || records.all { it.kind == RecordKind.Empty })
}

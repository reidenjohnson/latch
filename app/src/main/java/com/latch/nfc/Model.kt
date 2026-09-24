package com.latch.nfc

import android.net.Uri
import android.nfc.NdefMessage

enum class NfcAvailability { On, Off, Unsupported }

/** A user-facing explanation of what went wrong and what to do about it. */
data class Problem(val title: String, val detail: String, val needsPassword: Boolean = false)

class NfcProblem(val problem: Problem) : Exception(problem.title)

/** Produces the message for the i-th tag of a batch (0-based). */
class BatchJob(
    val title: String,
    /** Null = keep going until the user taps Finish. */
    val total: Int?,
    val lockAfter: Boolean,
    val mirror: Mirror?,
    val messageFor: (Int) -> NdefMessage,
    val labelFor: (Int) -> String,
)

enum class RawTech { NfcA, NfcB, NfcF, NfcV, IsoDep }

/** Something the user asked to do to the *next* tag they tap. Stays armed until it succeeds or is cancelled. */
sealed interface Operation {
    /** Password for NTAG21x write protection, when the tag has one. */
    val password: String? get() = null

    data class Write(
        val message: NdefMessage,
        val label: String,
        override val password: String? = null,
        val mirror: Mirror? = null,
    ) : Operation

    data class Erase(override val password: String? = null) : Operation
    data object CopySource : Operation
    data class CopyTarget(
        val message: NdefMessage, val label: String, val sourceUid: String, override val password: String? = null,
    ) : Operation
    data object MakeReadOnly : Operation

    data class Batch(
        val job: BatchJob,
        val done: Int = 0,
        val failed: Int = 0,
        val lastUid: String? = null,
        val log: List<String> = emptyList(),
        override val password: String? = null,
    ) : Operation {
        val finished get() = job.total != null && done >= job.total
    }

    data class ReadMany(val rows: List<TagSnapshot> = emptyList(), val lastUid: String? = null) : Operation
    data object Dump : Operation
    data class SetPassword(val newPassword: String, override val password: String? = null) : Operation
    data class RemovePassword(override val password: String? = null) : Operation
    data class Counter(val enable: Boolean, override val password: String? = null) : Operation
    data class Raw(val tech: RawTech, val commands: List<ByteArray>) : Operation
}

fun Operation.withPassword(pw: String): Operation = when (this) {
    is Operation.Write -> copy(password = pw)
    is Operation.Erase -> copy(password = pw)
    is Operation.CopyTarget -> copy(password = pw)
    is Operation.Batch -> copy(password = pw)
    is Operation.SetPassword -> copy(password = pw)
    is Operation.RemovePassword -> copy(password = pw)
    is Operation.Counter -> copy(password = pw)
    else -> this
}

/** Text output of an operation (memory dump, CSV, command log) that the user can read and share. */
data class Report(val text: String, val fileName: String, val mime: String = "text/plain")

sealed interface SheetState {
    data object Hidden : SheetState
    /** Waiting for a tag. [problem] is set when the last attempt failed and the next tap retries.
     *  [note] is a success message shown mid-flow ("Tag 3 written"). */
    data class Waiting(val op: Operation, val problem: Problem? = null, val note: String? = null) : SheetState
    data class Done(val op: Operation, val title: String, val detail: String?, val report: Report? = null) : SheetState
}

enum class RecordKind { Link, Text, WiFi, Contact, Phone, Sms, Email, Location, App, Bluetooth, Secret, Data, Empty, Unknown }

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
    /** Encrypted Latch secret payload. The UI offers an Unlock button. */
    val sealed: ByteArray? = null,
    /** Technical view: TNF, type and payload. */
    val raw: RawRecord? = null,
)

data class RawRecord(val tnf: Int, val type: String, val payloadSize: Int, val payloadHex: String)

data class TagSnapshot(
    val uid: String,
    val typeLabel: String,
    val ntag: NtagInfo?,
    val techs: List<String>,
    val atqa: String?,
    val sak: String?,
    val maxTransceive: Int?,
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
    /** Exact chip, only when the tag answered GET_VERSION. Never guessed. */
    val chip: String? get() = ntag?.chip?.label
    val isBlank: Boolean get() = supported && (message == null || records.all { it.kind == RecordKind.Empty })
}

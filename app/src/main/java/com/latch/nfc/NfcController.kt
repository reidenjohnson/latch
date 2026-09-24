package com.latch.nfc

import android.content.Context
import android.nfc.Tag
import com.latch.data.HistoryAction
import com.latch.data.HistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single owner of NFC state. MainActivity forwards every tag here from reader mode.
 *
 * If an operation is armed (the scan sheet is open), the tag goes to that operation. Otherwise, if the Read tab
 * is showing, the tag is read. Any other tap is ignored, so a tag bumped while filling in a form does nothing.
 */
class NfcController(private val context: Context, private val history: HistoryStore) {

    private val haptics = Haptics(context)

    private val _availability = MutableStateFlow(NfcAvailability.On)
    val availability: StateFlow<NfcAvailability> = _availability.asStateFlow()

    private val _sheet = MutableStateFlow<SheetState>(SheetState.Hidden)
    val sheet: StateFlow<SheetState> = _sheet.asStateFlow()

    private val _lastRead = MutableStateFlow<TagSnapshot?>(null)
    val lastRead: StateFlow<TagSnapshot?> = _lastRead.asStateFlow()

    private val _readProblem = MutableStateFlow<Problem?>(null)
    val readProblem: StateFlow<Problem?> = _readProblem.asStateFlow()

    @Volatile var passiveReadEnabled = false

    fun setAvailability(value: NfcAvailability) { _availability.value = value }
    fun arm(op: Operation) { _sheet.value = SheetState.Waiting(op) }
    fun dismiss() { _sheet.value = SheetState.Hidden }
    fun clearRead() { _lastRead.value = null; _readProblem.value = null }

    /** Called on the reader-mode binder thread. Blocking I/O is fine here. */
    fun onTag(tag: Tag) {
        when (val state = _sheet.value) {
            is SheetState.Waiting -> perform(state, tag)
            is SheetState.Done -> Unit
            SheetState.Hidden -> if (passiveReadEnabled) read(tag)
        }
    }

    private fun read(tag: Tag) {
        try {
            val snapshot = TagIo.read(tag, context)
            _lastRead.value = snapshot
            _readProblem.value = null
            history.add(HistoryAction.Read, snapshot.summary, snapshot.chip ?: snapshot.typeLabel, snapshot.message)
            haptics.success()
        } catch (e: Exception) {
            _readProblem.value = TagIo.explain(e)
            haptics.error()
        }
    }

    private fun perform(state: SheetState.Waiting, tag: Tag) {
        val next: SheetState = try {
            when (val op = state.op) {
                is Operation.Write -> {
                    val report = TagIo.write(tag, op.message)
                    history.add(HistoryAction.Write, op.label, null, op.message)
                    done(op, "Written", op.label, report)
                }
                Operation.Erase -> {
                    TagIo.write(tag, Payloads.EMPTY)
                    history.add(HistoryAction.Erase, "Erased a tag", null, null)
                    SheetState.Done(op, "Tag erased", "It's blank and ready to reuse.")
                }
                Operation.CopySource -> {
                    val source = TagIo.read(tag, context)
                    if (!source.supported) throw NfcProblem(Problems.unsupported)
                    if (source.isBlank) throw NfcProblem(Problem("That tag is empty", "Tap the tag you want to copy from."))
                    SheetState.Waiting(Operation.CopyTarget(source.message!!, source.summary, source.uid))
                }
                is Operation.CopyTarget -> {
                    if (TagIo.uid(tag) == op.sourceUid) {
                        throw NfcProblem(Problem("That's the same tag", "Tap the new tag you want to copy onto."))
                    }
                    val report = TagIo.write(tag, op.message)
                    history.add(HistoryAction.Copy, op.label, null, op.message)
                    done(op, "Copied", op.label, report)
                }
                Operation.MakeReadOnly -> {
                    val type = TagIo.lock(tag)
                    history.add(HistoryAction.Lock, "Locked a tag", type, null)
                    SheetState.Done(op, "Tag locked", "It can still be read, but never changed again.")
                }
            }
        } catch (e: Exception) {
            SheetState.Waiting(state.op, TagIo.explain(e))
        }
        // Only apply the result if the user hasn't cancelled the sheet in the meantime.
        if (_sheet.compareAndSet(state, next)) {
            when {
                next is SheetState.Waiting && next.problem != null -> haptics.error()
                next is SheetState.Waiting -> haptics.tick()
                else -> haptics.success()
            }
        }
    }

    private fun done(op: Operation, verb: String, label: String, r: WriteReport): SheetState.Done {
        // "Verified" is claimed only when the read-back actually matched.
        val title = if (r.verified) "$verb and verified" else verb
        val detail = buildString {
            append(label)
            if (r.capacity != null) append("\n${r.size} of ${r.capacity} bytes used")
            if (!r.verified) append("\nCouldn't double-check it. Scan it on the Read tab to confirm.")
        }
        return SheetState.Done(op, title, detail)
    }
}

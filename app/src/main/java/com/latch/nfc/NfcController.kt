package com.latch.nfc

import android.content.Context
import android.nfc.Tag
import android.util.Log
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
    private companion object {
        const val TAG = "Latch"
        const val DONE_GRACE_MS = 1200L
    }

    private val _availability = MutableStateFlow(NfcAvailability.On)
    val availability: StateFlow<NfcAvailability> = _availability.asStateFlow()

    private val _sheet = MutableStateFlow<SheetState>(SheetState.Hidden)
    val sheet: StateFlow<SheetState> = _sheet.asStateFlow()

    private val _lastRead = MutableStateFlow<TagSnapshot?>(null)
    val lastRead: StateFlow<TagSnapshot?> = _lastRead.asStateFlow()

    private val _readProblem = MutableStateFlow<Problem?>(null)
    val readProblem: StateFlow<Problem?> = _readProblem.asStateFlow()

    fun setAvailability(value: NfcAvailability) { _availability.value = value }
    fun arm(op: Operation) { _sheet.value = SheetState.Waiting(op) }
    fun dismiss() { Log.d(TAG, "sheet dismissed"); _sheet.value = SheetState.Hidden }
    fun clearRead() { _lastRead.value = null; _readProblem.value = null }

    /** Supplies a password after the sheet asked for one. The next tap retries with it. */
    fun providePassword(pw: String) {
        val s = _sheet.value as? SheetState.Waiting ?: return
        _sheet.value = SheetState.Waiting(s.op.withPassword(pw), note = "Password set. Tap the tag again.")
    }

    /** Ends an open-ended batch or scan-many run and shows its report. */
    fun finish() {
        val s = _sheet.value as? SheetState.Waiting ?: return
        _sheet.value = when (val op = s.op) {
            is Operation.Batch -> batchDone(op)
            is Operation.ReadMany -> readManyDone(op)
            else -> SheetState.Hidden
        }
    }

    /** Called on the reader-mode binder thread. Blocking I/O is fine here. */
    fun onTag(tag: Tag) {
        Log.d(TAG, "onTag uid=${TagIo.uid(tag)} sheet=${_sheet.value::class.simpleName} techs=${tag.techList.size}")
        // Every tap does something. With nothing armed, the tag is read on any tab (the UI offers "View" off the Read tab).
        when (val state = _sheet.value) {
            is SheetState.Waiting -> perform(state, tag)
            is SheetState.Done -> {
                // Ignore the same tag bouncing right after a success. After that, a tap means "next", so never get
                // stuck on a finished result if the sheet's close animation didn't report back.
                if (System.currentTimeMillis() - state.at > DONE_GRACE_MS && state.report == null) {
                    _sheet.compareAndSet(state, SheetState.Hidden)
                    read(tag)
                }
            }
            SheetState.Hidden -> read(tag)
        }
    }

    private fun read(tag: Tag) {
        try {
            val snapshot = TagIo.read(tag, context)
            _lastRead.value = snapshot
            _readProblem.value = null
            history.add(HistoryAction.Read, snapshot.summary, snapshot.chip ?: snapshot.typeLabel, snapshot.message)
            Log.d(TAG, "read ok: ${snapshot.summary} records=${snapshot.records.size}")
            haptics.success()
        } catch (e: Throwable) {
            Log.w(TAG, "read failed", e)
            _readProblem.value = TagIo.explain(e)
            haptics.error()
        }
    }

    private fun perform(state: SheetState.Waiting, tag: Tag) {
        val op = state.op
        val next: SheetState = try {
            when (op) {
                is Operation.Write -> {
                    val report = TagIo.write(tag, op.message, op.password, op.mirror)
                    history.add(HistoryAction.Write, op.label, null, op.message)
                    done(op, "Written", op.label, report)
                }
                is Operation.Erase -> {
                    TagIo.write(tag, Payloads.EMPTY, op.password)
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
                    if (TagIo.uid(tag) == op.sourceUid) throw NfcProblem(Problem("That's the same tag", "Tap the new tag you want to copy onto."))
                    val report = TagIo.write(tag, op.message, op.password)
                    history.add(HistoryAction.Copy, op.label, null, op.message)
                    done(op, "Copied", op.label, report)
                }
                Operation.MakeReadOnly -> {
                    val type = TagIo.lock(tag)
                    history.add(HistoryAction.Lock, "Locked a tag", type, null)
                    SheetState.Done(op, "Tag locked", "It can still be read, but never changed again.")
                }
                is Operation.Batch -> batchStep(op, tag)
                is Operation.ReadMany -> {
                    val uid = TagIo.uid(tag)
                    if (uid == op.lastUid) throw NfcProblem(Problem("Already scanned that one", "Tap the next tag."))
                    val s = TagIo.read(tag, context)
                    history.add(HistoryAction.Read, s.summary, s.chip ?: s.typeLabel, s.message)
                    SheetState.Waiting(op.copy(rows = op.rows + s, lastUid = uid), note = "${op.rows.size + 1} scanned · ${s.summary}")
                }
                Operation.Dump -> {
                    val report = TagIo.dump(tag)
                    SheetState.Done(op, "Memory read", "${report.text.lines().size - 4} pages. Share or save it below.", report)
                }
                is Operation.SetPassword -> {
                    val chip = TagIo.setPassword(tag, op.newPassword, op.password)
                    SheetState.Done(op, "Password set", "${chip.label}: anyone can still read it, but changing or erasing it now needs your password.")
                }
                is Operation.RemovePassword -> {
                    TagIo.removePassword(tag, op.password)
                    SheetState.Done(op, "Password removed", "The tag is open again.")
                }
                is Operation.Counter -> {
                    TagIo.setCounter(tag, op.enable, op.password)
                    SheetState.Done(
                        op, if (op.enable) "Scan counter on" else "Scan counter off",
                        if (op.enable) "The tag now counts every time it's read. See it on the Read tab." else null,
                    )
                }
                is Operation.Raw -> {
                    val report = TagIo.raw(tag, op.tech, op.commands)
                    SheetState.Done(op, "Commands sent", null, report)
                }
            }
        } catch (e: Exception) {
            val problem = TagIo.explain(e)
            if (op is Operation.Batch && !problem.needsPassword && e !is NfcProblem) {
                SheetState.Waiting(op.copy(failed = op.failed + 1), problem)
            } else SheetState.Waiting(op, problem, state.note.takeIf { op is Operation.Batch || op is Operation.ReadMany })
        }
        // Only apply the result if the user hasn't cancelled the sheet in the meantime.
        if (_sheet.compareAndSet(state, next)) {
            when {
                next is SheetState.Waiting && next.problem != null -> haptics.error()
                next is SheetState.Waiting -> haptics.success()
                else -> haptics.success()
            }
        }
    }

    private fun batchStep(op: Operation.Batch, tag: Tag): SheetState {
        val uid = TagIo.uid(tag)
        if (uid == op.lastUid) throw NfcProblem(Problem("That's the tag you just wrote", "Tap the next one."))
        val job = op.job
        val message = job.messageFor(op.done)
        val label = job.labelFor(op.done)
        TagIo.write(tag, message, op.password, job.mirror)
        if (job.lockAfter) TagIo.lock(tag)
        history.add(HistoryAction.Write, label, null, message)
        val next = op.copy(done = op.done + 1, lastUid = uid, log = op.log + "${op.done + 1},$uid,\"${label.replace("\"", "\"\"")}\"")
        return if (next.finished) batchDone(next)
        else SheetState.Waiting(next, note = "Tag ${next.done}${job.total?.let { " of $it" } ?: ""} written ✓ Tap the next one.")
    }

    private fun batchDone(op: Operation.Batch) = SheetState.Done(
        op, "Batch finished",
        "${op.done} written${if (op.failed > 0) " · ${op.failed} retried" else ""}${if (op.job.lockAfter) " and locked" else ""}.",
        Report("#,uid,content\n" + op.log.joinToString("\n"), "latch-batch.csv", "text/csv"),
    )

    private fun readManyDone(op: Operation.ReadMany): SheetState {
        val csv = buildString {
            appendLine("#,time,uid,chip,type,summary")
            op.rows.forEachIndexed { i, s ->
                appendLine("${i + 1},${s.time},${s.uid},${s.chip.orEmpty()},${s.typeLabel},\"${s.summary.replace("\"", "\"\"")}\"")
            }
        }
        return SheetState.Done(op, "${op.rows.size} tags scanned", "Share the list as a CSV spreadsheet.", Report(csv, "latch-scans.csv", "text/csv"))
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

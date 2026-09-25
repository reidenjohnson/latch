package com.latch.focus.nfc

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.latch.focus.data.Change
import com.latch.focus.engine.Engine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

sealed interface PairState {
    data object Idle : PairState
    data object Waiting : PairState
    /** [lockedTag]: the tag is read-only, so it was paired by ID only and works only while Latch is open. */
    data class Paired(val lockedTag: Boolean) : PairState
    data class Failed(val title: String, val detail: String) : PairState
}

/** What happened on the last tap that wasn't a pairing, for a quick in-app message. */
sealed interface TapNote {
    data object Unpaired : TapNote
}

/**
 * Handles every tag tapped while Latch is on screen (reader mode). If pairing is armed, the tag is written and
 * paired. Otherwise a paired tag starts or ends a session, like it does with Latch closed.
 */
class TagReader(private val context: Context, private val engine: Engine) {
    private val _pair = MutableStateFlow<PairState>(PairState.Idle)
    val pair: StateFlow<PairState> = _pair.asStateFlow()

    private val _note = MutableStateFlow<Pair<Long, TapNote>?>(null)
    val note: StateFlow<Pair<Long, TapNote>?> = _note.asStateFlow()

    fun startPairing() { _pair.value = PairState.Waiting }
    fun cancelPairing() { _pair.value = PairState.Idle }

    /** Called on the reader-mode binder thread, so blocking I/O is fine. */
    fun onTag(tag: Tag) {
        val uid = uid(tag)
        Log.d("Latch", "tag $uid pairing=${_pair.value}")
        if (_pair.value is PairState.Waiting || _pair.value is PairState.Failed) {
            _pair.value = pairTag(tag, uid)
            buzz(_pair.value is PairState.Paired)
            return
        }
        if (!engine.state.value.isPaired(uid)) {
            _note.value = System.nanoTime() to TapNote.Unpaired
            buzz(false)
            return
        }
        val c = engine.tap(uid)
        buzz(c is Change.Started || c is Change.Ended)
    }

    private fun pairTag(tag: Tag, uid: String): PairState {
        if (engine.locked) return PairState.Failed("You're latched", "Unlatch first, then pair a new Latch.")
        return try {
            val locked = !write(tag)
            engine.pair(uid)
            PairState.Paired(locked)
        } catch (e: TagLostException) {
            PairState.Failed("Tag moved away too soon", "Hold your phone still on the tag until it buzzes.")
        } catch (e: Unsupported) {
            PairState.Failed("This tag can't be used", "It looks like a bank or transit card. Use an NFC sticker, card or key fob.")
        } catch (e: TooSmall) {
            PairState.Failed("That tag is too small", "Use an NTAG213, 215 or 216 sticker. Almost every NFC sticker is one of these.")
        } catch (e: IOException) {
            PairState.Failed("Couldn't write to the tag", "Hold the middle of your phone's back flat on the tag and try again.")
        } catch (e: Exception) {
            PairState.Failed("Couldn't pair that tag", e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Writes the Latch record and reads it back. Returns false if the tag is read-only (it can still be paired by ID).
     * Anything already on the tag is replaced.
     */
    private fun write(tag: Tag): Boolean {
        val message = message()
        val bytes = message.toByteArray()
        Ndef.get(tag)?.let { ndef ->
            ndef.use {
                it.connect()
                if (!it.isWritable) return false
                if (bytes.size > it.maxSize) throw TooSmall()
                it.writeNdefMessage(message)
                val back = runCatching { it.ndefMessage?.toByteArray() }.getOrNull()
                if (back != null && !back.contentEquals(bytes)) throw IOException("read-back mismatch")
                return true
            }
        }
        val formatable = NdefFormatable.get(tag) ?: throw Unsupported()
        formatable.use { it.connect(); it.format(message) }
        return true
    }

    private fun buzz(ok: Boolean) = runCatching {
        if (!com.latch.focus.engine.Prefs.haptics.value) return@runCatching
        val v = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return@runCatching
        v.vibrate(
            if (ok) VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            else VibrationEffect.createWaveform(longArrayOf(0, 90, 70, 90), -1),
        )
    }

    private class Unsupported : Exception()
    private class TooSmall : Exception()

    companion object {
        /**
         * A Latch tag holds an NFC Forum external record, which Latch's TapActivity filters for, plus an Android
         * Application Record so a tap with Latch closed opens Latch (or its store page if it isn't installed).
         * https://developer.android.com/develop/connectivity/nfc/nfc#ext-type
         * The record is the same one Latch Tags wrote, so tags paired before the split keep working.
         */
        const val DOMAIN = "com.latch"
        const val KIND = "focus"

        fun message() = NdefMessage(
            NdefRecord.createExternal(DOMAIN, KIND, byteArrayOf(1)),
            NdefRecord.createApplicationRecord("com.latch"),
        )

        fun uid(tag: Tag): String = tag.id.joinToString(":") { "%02X".format(it) }
    }
}

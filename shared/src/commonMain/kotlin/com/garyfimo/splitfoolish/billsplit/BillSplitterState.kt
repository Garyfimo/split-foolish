package com.garyfimo.splitfoolish.billsplit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlin.math.roundToLong

class EditableParticipant(
    val id: Long,
    name: String,
    percentInput: String,
) {
    var name: String by mutableStateOf(name)
    var percentInput: String by mutableStateOf(percentInput)
}

class BillSplitterState {
    private var idCounter: Long = 0
    private fun nextId(): Long = idCounter++

    var totalInput: String by mutableStateOf("")
    var mode: SplitMode by mutableStateOf(SplitMode.Equal)

    val participants: SnapshotStateList<EditableParticipant> =
        mutableStateListOf<EditableParticipant>().apply {
            add(EditableParticipant(nextId(), "Alice", ""))
            add(EditableParticipant(nextId(), "Bob", ""))
        }

    val totalCents: Long? get() = parseAmountToCents(totalInput)

    /**
     * In percentage mode the last row (including the sole row when there is just one participant)
     * always auto-balances to 100 − sum(others); the stored percentInput on that row is ignored
     * while it holds that position. `isAutoBalanced` is the single source of truth so the UI
     * (read-only field) and the result computation can't drift apart.
     */
    fun displayedPercent(index: Int): Double {
        val raw = participants.getOrNull(index) ?: return 0.0
        return if (isAutoBalanced(index)) {
            val othersSum = participants.take(index).sumOf { it.percentInput.toDoubleOrNull() ?: 0.0 }
            100.0 - othersSum
        } else {
            raw.percentInput.toDoubleOrNull() ?: 0.0
        }
    }

    fun isAutoBalanced(index: Int): Boolean =
        mode == SplitMode.Percentage && index == participants.lastIndex

    fun addParticipant() {
        if (mode == SplitMode.Percentage && participants.isNotEmpty()) {
            val lockedIn = displayedPercent(participants.lastIndex)
            participants[participants.lastIndex].percentInput = formatPercentForInput(lockedIn)
        }
        participants.add(EditableParticipant(nextId(), "Person ${participants.size + 1}", "0"))
    }

    fun removeParticipant(id: Long) {
        participants.removeAll { it.id == id }
    }

    val result: SplitResult?
        get() {
            val total = totalCents ?: return null
            val domainParticipants = participants.mapIndexed { idx, p ->
                Participant(p.id, p.name, displayedPercent(idx))
            }
            return splitBill(total, mode, domainParticipants)
        }
}

private fun formatPercentForInput(value: Double): String {
    val rounded = (value * 100.0).roundToLong() / 100.0
    return rounded.toString()
}

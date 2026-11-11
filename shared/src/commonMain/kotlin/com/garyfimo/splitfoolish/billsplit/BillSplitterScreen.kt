package com.garyfimo.splitfoolish.billsplit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun BillSplitterScreen(
    state: BillSplitterState = remember { BillSplitterState() },
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Split the bill", style = MaterialTheme.typography.headlineMedium)

                TotalField(state)
                ModeSelector(state)
                ParticipantsSection(state)
                SummarySection(state)
            }
        }
    }
}

@Composable
private fun TotalField(state: BillSplitterState) {
    OutlinedTextField(
        value = state.totalInput,
        onValueChange = { state.totalInput = it },
        label = { Text("Total amount") },
        prefix = { Text(currencySymbol()) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = state.totalInput.isNotBlank() && state.totalCents == null,
        supportingText = {
            if (state.totalInput.isNotBlank() && state.totalCents == null) {
                Text("Enter a valid amount")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModeSelector(state: BillSplitterState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Split by", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChoice(
                label = "Equal",
                selected = state.mode == SplitMode.Equal,
                onClick = { state.mode = SplitMode.Equal },
                modifier = Modifier.weight(1f),
            )
            ModeChoice(
                label = "Percentage",
                selected = state.mode == SplitMode.Percentage,
                onClick = { state.mode = SplitMode.Percentage },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun ParticipantsSection(state: BillSplitterState) {
    val shares = (state.result as? SplitResult.Success)?.shares
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("People", style = MaterialTheme.typography.labelLarge)
        state.participants.forEachIndexed { index, participant ->
            val share = shares?.firstOrNull { it.participant.id == participant.id }?.cents
            ParticipantCard(
                index = index,
                participant = participant,
                state = state,
                shareCents = share,
                canRemove = state.participants.size > 1,
                onRemove = { state.removeParticipant(participant.id) },
            )
        }
        FilledTonalButton(
            onClick = { state.addParticipant() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Add person")
        }
    }
}

@Composable
private fun ParticipantCard(
    index: Int,
    participant: EditableParticipant,
    state: BillSplitterState,
    shareCents: Long?,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = participant.name,
                    onValueChange = { participant.name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onRemove) { Text("Remove") }
                }
            }

            if (state.mode == SplitMode.Percentage) {
                val isAuto = state.isAutoBalanced(index)
                val displayed = state.displayedPercent(index)
                OutlinedTextField(
                    value = if (isAuto) formatPercentDisplay(displayed) else participant.percentInput,
                    onValueChange = { participant.percentInput = it },
                    label = { Text(if (isAuto) "Percent (auto)" else "Percent") },
                    suffix = { Text("%") },
                    singleLine = true,
                    readOnly = isAuto,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (shareCents != null) {
                Text(
                    text = "Pays: ${formatCents(shareCents)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SummarySection(state: BillSplitterState) {
    val result = state.result
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Summary", style = MaterialTheme.typography.labelLarge)
        when {
            state.totalInput.isBlank() -> Text("Enter a total above to see the split.")
            result is SplitResult.Success -> {
                val total = state.totalCents ?: 0L
                Text(
                    "Total ${formatCents(total)} across ${result.shares.size} people.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            result is SplitResult.Failure -> Text(
                errorMessage(result.error),
                color = MaterialTheme.colorScheme.error,
            )
            else -> Text("Enter a valid amount to see the split.")
        }
    }
}

private fun errorMessage(error: SplitError): String = when (error) {
    SplitError.NoParticipants -> "Add at least one person."
    SplitError.NegativeTotal -> "Total can't be negative."
    SplitError.NegativePercentage -> "Percentages can't be negative."
    SplitError.PercentagesDoNotSumToHundred -> "Percentages must add up to 100%."
}

private fun formatPercentDisplay(value: Double): String {
    val rounded = ((value * 100.0).toLong()) / 100.0
    return rounded.toString()
}

package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garyfimo.splitfoolish.core.ui.BackIconButton

/**
 * Create-a-group screen, simplified for the Firestore multi-user model: the creator is
 * automatically added as the sole member. Additional members are invited from GroupDetailScreen's
 * "Add member" dialog (by email). The old friends-picker flow is gone because real-user-only
 * members can't be fabricated from a local name string — they need a Firebase Auth uid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    onCreateGroup: (name: String) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val canCreate = name.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New group") },
                navigationIcon = { BackIconButton(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name (e.g. Beach trip)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "You'll be added as the first member. Invite others by email from the group screen.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onCreateGroup(name.trim()) },
                    enabled = canCreate,
                    modifier = Modifier.weight(1f),
                ) { Text("Create") }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
            }
        }
    }
}

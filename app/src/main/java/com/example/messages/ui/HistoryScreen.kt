package com.example.messages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shows what is really stored in the phone's shared SMS storage (what every SMS app reads). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MessageViewModel, onBack: () -> Unit) {
    val items by vm.history.collectAsStateWithLifecycle()
    val mine by vm.mine.collectAsStateWithLifecycle()
    val canRead by vm.canReadHistory.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    LaunchedEffect(Unit) { vm.loadHistory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone history") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { vm.loadHistory() }) { Text("Refresh") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "This is the phone's shared message storage. Every SMS app reads from it, " +
                    "so anything listed here can show up in them. Messages added by this app " +
                    "are marked.",
                style = MaterialTheme.typography.bodySmall
            )
            if (!canRead) {
                Text(
                    "Make Messages the default SMS app to read the phone's history.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { s ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(s.address, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    fmt.format(Date(s.date)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(s.body, style = MaterialTheme.typography.bodyMedium)
                            val kind = when (s.type) {
                                1 -> "Inbox"
                                2 -> "Sent"
                                3 -> "Draft"
                                else -> "Other"
                            }
                            Text(
                                if (s.id in mine) "$kind · added by Messages" else kind,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

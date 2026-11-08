package com.example.messages

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.data.Message
import com.example.messages.theme.MessagesTheme
import com.example.messages.ui.MessageViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MessagesTheme {
                AppScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: MessageViewModel) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Message?>(null) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                actions = {
                    if (!showEditor) {
                        TextButton(onClick = { editing = null; showEditor = true }) {
                            Text("New")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("inbox" to "Inbox", "outbox" to "Sent", "draft" to "Drafts").forEach { (key, label) ->
                    FilterChip(
                        selected = folder == key,
                        onClick = { vm.setFolder(key); showEditor = false; editing = null },
                        label = { Text(label) }
                    )
                }
            }

            if (showEditor) {
                EditorScreen(
                    existing = editing,
                    onSave = { msg ->
                        vm.save(msg)
                        showEditor = false
                        editing = null
                    },
                    onCancel = { showEditor = false; editing = null }
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(messages, key = { it.id }) { m ->
                        MessageCard(
                            message = m,
                            onEdit = { editing = m; showEditor = true },
                            onDelete = { vm.delete(m) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageCard(message: Message, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(message.recipient, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${message.date} ${message.time}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(message.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
fun EditorScreen(existing: Message?, onSave: (Message) -> Unit, onCancel: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var recipient by remember { mutableStateOf(existing?.recipient ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: dateFmt.format(Date())) }
    var time by remember { mutableStateOf(existing?.time ?: timeFmt.format(Date())) }
    var folder by remember { mutableStateOf(existing?.folder ?: "draft") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = recipient,
            onValueChange = { recipient = it },
            label = { Text("To (name or phone)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Date") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                label = { Text("Time") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("draft" to "Draft", "outbox" to "Sent", "inbox" to "Inbox").forEach { (key, label) ->
                FilterChip(
                    selected = folder == key,
                    onClick = { folder = key },
                    label = { Text(label) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (recipient.isNotBlank() && body.isNotBlank()) {
                        onSave(
                            Message(
                                id = existing?.id ?: 0,
                                recipient = recipient.trim(),
                                body = body.trim(),
                                date = date,
                                time = time,
                                folder = folder
                            )
                        )
                    }
                },
                enabled = recipient.isNotBlank() && body.isNotBlank()
            ) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

package com.example.messages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.messages.data.ScheduledMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    svm: ScheduleViewModel,
    onBack: () -> Unit,
    onRequestDefault: () -> Unit
) {
    val items by svm.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val isDefault by svm.isDefault.collectAsStateWithLifecycle()
    val recent by svm.recent.collectAsStateWithLifecycle()

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val fullFmt = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
    }
    val soon = remember { Date() }

    var recipient by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(dateFmt.format(soon)) }
    var time by remember { mutableStateOf(timeFmt.format(soon)) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled texts") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isDefault) {
                            Text(
                                "Messages is the default SMS app, so scheduled texts will be " +
                                    "delivered. Keep it as the default until they have fired, " +
                                    "then switch back to your usual app.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                "Messages is not the default SMS app right now. Scheduled texts " +
                                    "can only be delivered while it is the default.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = onRequestDefault) { Text("Make default") }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (recent.isNotEmpty()) {
                        Text(
                            "Continue an existing conversation:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recent) { addr ->
                                FilterChip(
                                    selected = recipient == addr,
                                    onClick = { recipient = addr },
                                    label = { Text(addr) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = { Text("From (phone number)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Date (yyyy-MM-dd)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text("Time (HH:mm)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    info?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = {
                            info = null
                            val millis = try {
                                fullFmt.parse("${date.trim()} ${time.trim()}")?.time
                            } catch (_: Exception) {
                                null
                            }
                            when {
                                recipient.isBlank() || body.isBlank() ->
                                    error = "Enter a number and a message."
                                millis == null ->
                                    error = "Use the format yyyy-MM-dd and HH:mm (24-hour)."
                                !isDefault ->
                                    error = "Make Messages the default SMS app first."
                                else -> {
                                    error = null
                                    svm.sendNow(recipient.trim(), body.trim(), millis) { ok ->
                                        if (ok) {
                                            body = ""
                                            info = "Sent. Switch back to your messaging app to see it."
                                        } else {
                                            error = "Could not write the text. Is Messages still the default app?"
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send now") }
                    OutlinedButton(
                        onClick = {
                            info = null
                            val fireAt = try {
                                fullFmt.parse("${date.trim()} ${time.trim()}")?.time
                            } catch (_: Exception) {
                                null
                            }
                            when {
                                recipient.isBlank() || body.isBlank() ->
                                    error = "Enter a number and a message."
                                fireAt == null ->
                                    error = "Use the format yyyy-MM-dd and HH:mm (24-hour)."
                                fireAt <= System.currentTimeMillis() ->
                                    error = "Pick a time in the future."
                                else -> {
                                    error = null
                                    svm.schedule(recipient.trim(), body.trim(), fireAt)
                                    body = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Or schedule for that time") }
                }
            }

            item {
                Text("Scheduled", style = MaterialTheme.typography.titleMedium)
            }

            items(items, key = { it.id }) { s ->
                ScheduledCard(s, fullFmt, onRemove = { svm.remove(s.id) })
            }
        }
    }
}

@Composable
private fun ScheduledCard(s: ScheduledMessage, fmt: SimpleDateFormat, onRemove: () -> Unit) {
    val status = when (s.status) {
        "pending" -> "Waiting"
        "sent" -> "Delivered"
        "missed" -> "Missed (Messages was not the default app)"
        else -> "Failed"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(s.recipient, style = MaterialTheme.typography.titleMedium)
                Text(fmt.format(Date(s.fireAt)), style = MaterialTheme.typography.bodySmall)
            }
            Text(s.body, style = MaterialTheme.typography.bodyMedium)
            Text(status, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRemove) {
                Text(if (s.status == "pending") "Cancel" else "Remove")
            }
        }
    }
}

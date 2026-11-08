package com.example.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import com.example.messages.ui.HistoryScreen
import com.example.messages.ui.ScheduleScreen
import com.example.messages.ui.ScheduleViewModel
import com.example.messages.ui.SyncUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MessageViewModel by viewModels()
    private val svm: ScheduleViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val defaultSmsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vm.onDefaultResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MessagesTheme {
                val sync by vm.sync.collectAsStateWithLifecycle()
                var showSchedule by remember { mutableStateOf(false) }
                var showHistory by remember { mutableStateOf(false) }

                if (showHistory) {
                    BackHandler { showHistory = false }
                    HistoryScreen(vm = vm, onBack = { showHistory = false })
                } else if (showSchedule) {
                    BackHandler { showSchedule = false }
                    ScheduleScreen(
                        svm = svm,
                        onBack = { showSchedule = false },
                        onRequestDefault = { requestDefaultSms() }
                    )
                } else {
                    AppScreen(
                        vm = vm,
                        sync = sync,
                        onRequestDefault = { requestDefaultSms() },
                        onOpenDefaultAppSettings = {
                            vm.dismissSync()
                            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        },
                        onOpenSchedule = { showSchedule = true },
                        onOpenHistory = { showHistory = true }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        svm.refresh()
    }

    private fun requestDefaultSms() {
        vm.rememberPreviousDefault()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java)
                .createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
        defaultSmsLauncher.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    vm: MessageViewModel,
    sync: SyncUi,
    onRequestDefault: () -> Unit,
    onOpenDefaultAppSettings: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenHistory: () -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Message?>(null) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle(initialValue = emptyList())

    when (sync) {
        is SyncUi.NeedDefault -> AlertDialog(
            onDismissRequest = { vm.dismissSync() },
            title = { Text("Add to your messaging app?") },
            text = {
                Text(
                    "Android only lets the default SMS app write to the phone's message store. " +
                        "Make Messages the default for a moment so your ${sync.pending} new " +
                        "message(s) are added, then switch back to your usual app."
                )
            },
            confirmButton = { TextButton(onClick = onRequestDefault) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { vm.dismissSync() }) { Text("Not now") } }
        )

        is SyncUi.Done -> AlertDialog(
            onDismissRequest = { vm.dismissSync() },
            title = { Text("Messages added") },
            text = {
                Text(
                    "${sync.count} message(s) were written to the phone's SMS storage and " +
                        "checked there. Now switch back to " +
                        "${sync.previousApp ?: "your messaging app"} so they appear there. " +
                        "While Messages stays the default, new incoming texts will not show " +
                        "notifications."
                )
            },
            confirmButton = {
                TextButton(onClick = onOpenDefaultAppSettings) { Text("Open default apps") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissSync() }) { Text("Close") } }
        )

        SyncUi.Idle -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                actions = {
                    if (!showEditor) {
                        TextButton(onClick = onOpenHistory) { Text("History") }
                        TextButton(onClick = onOpenSchedule) { Text("Schedule") }
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
            if (message.synced) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "✓ Saved in the phone's message storage",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }

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
            label = { Text("Phone number") },
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
                                folder = folder,
                                createdAt = existing?.createdAt ?: System.currentTimeMillis()
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

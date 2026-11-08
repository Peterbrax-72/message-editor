package com.example.messages.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.Delivery
import com.example.messages.Scheduler
import com.example.messages.data.AppDatabase
import com.example.messages.data.ScheduledMessage
import com.example.messages.data.SmsSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val dao = AppDatabase.get(app).scheduledDao()

    val items: Flow<List<ScheduledMessage>> = dao.observeAll()

    private val _isDefault = MutableStateFlow(SmsSync.isDefault(ctx))
    val isDefault: StateFlow<Boolean> = _isDefault

    /** Numbers from existing conversations (only readable while this is the default SMS app). */
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent

    fun refresh() {
        val default = SmsSync.isDefault(ctx)
        _isDefault.value = default
        if (default) {
            viewModelScope.launch {
                _recent.value = withContext(Dispatchers.IO) { SmsSync.recentAddresses(ctx) }
            }
        } else {
            _recent.value = emptyList()
        }
    }

    fun schedule(recipient: String, body: String, fireAt: Long) = viewModelScope.launch {
        val id = dao.insert(ScheduledMessage(recipient = recipient, body = body, fireAt = fireAt))
        Scheduler.schedule(ctx, ScheduledMessage(id, recipient, body, fireAt))
    }

    /** Delivers the text right now, showing [millis] as its date and time. */
    fun sendNow(recipient: String, body: String, millis: Long, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                Delivery.deliver(
                    ctx, recipient, body, millis,
                    (System.currentTimeMillis() % 100000).toInt()
                )
            }
            onDone(ok)
        }

    fun remove(id: Long) = viewModelScope.launch {
        Scheduler.cancel(ctx, id)
        dao.delete(id)
    }
}

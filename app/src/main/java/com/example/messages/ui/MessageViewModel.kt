package com.example.messages.ui

import android.app.Application
import android.content.Context
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.data.AppDatabase
import com.example.messages.data.Message
import com.example.messages.data.SmsSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SyncUi {
    data object Idle : SyncUi
    /** New messages exist that are not in the phone's SMS store yet. */
    data class NeedDefault(val pending: Int) : SyncUi
    /** Messages were just written; the user should switch back to their usual app. */
    data class Done(val count: Int, val previousApp: String?) : SyncUi
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val dao = AppDatabase.get(app).messageDao()
    private val prefs = app.getSharedPreferences("sms_prefs", Context.MODE_PRIVATE)
    private val syncLock = Mutex()

    private val _folder = MutableStateFlow("inbox")
    val folder: StateFlow<String> = _folder

    val messages: Flow<List<Message>> = _folder.flatMapLatest { dao.getByFolder(it) }

    private val _sync = MutableStateFlow<SyncUi>(SyncUi.Idle)
    val sync: StateFlow<SyncUi> = _sync

    fun setFolder(f: String) { _folder.value = f }

    fun save(message: Message) = viewModelScope.launch {
        if (message.id == 0L) {
            dao.insert(message.copy(synced = false))
        } else {
            val old = dao.getById(message.id)
            dao.update(message.copy(sysId = old?.sysId ?: 0L, synced = false))
        }
        val pending = dao.getUnsynced().size
        if (pending > 0) {
            if (SmsSync.isDefault(ctx)) syncPending() else _sync.value = SyncUi.NeedDefault(pending)
        }
    }

    fun delete(message: Message) = viewModelScope.launch {
        // The phone's SMS store can only be changed while this app is the default SMS app.
        if (message.sysId != 0L && SmsSync.isDefault(ctx)) {
            withContext(Dispatchers.IO) { SmsSync.delete(ctx, message.sysId) }
        }
        dao.delete(message)
    }

    fun load(id: Long, onLoaded: (Message?) -> Unit) = viewModelScope.launch {
        onLoaded(dao.getById(id))
    }

    /** Remember which app was the default so we can tell the user to switch back to it. */
    fun rememberPreviousDefault() {
        val current = Telephony.Sms.getDefaultSmsPackage(ctx)
        if (current != null && current != ctx.packageName) {
            prefs.edit().putString("prev_default", current).apply()
        }
    }

    /** Called when the "make default" system dialog closes. */
    fun onDefaultResult() = viewModelScope.launch {
        if (SmsSync.isDefault(ctx)) syncPending() else _sync.value = SyncUi.Idle
    }

    /** Called from onResume: if we are default and something is waiting, write it. */
    fun onResume() = viewModelScope.launch {
        if (SmsSync.isDefault(ctx) && dao.getUnsynced().isNotEmpty()) syncPending()
    }

    fun dismissSync() { _sync.value = SyncUi.Idle }

    private suspend fun syncPending() = syncLock.withLock {
        var written = 0
        for (m in dao.getUnsynced()) {
            val sysId = withContext(Dispatchers.IO) { SmsSync.upsert(ctx, m) }
            if (sysId != null) {
                dao.markSynced(m.id, sysId)
                written++
            }
        }
        if (written > 0) _sync.value = SyncUi.Done(written, previousAppLabel())
    }

    private fun previousAppLabel(): String? {
        val pkg = prefs.getString("prev_default", null) ?: return null
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            null
        }
    }
}

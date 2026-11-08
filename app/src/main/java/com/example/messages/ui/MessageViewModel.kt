package com.example.messages.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.data.AppDatabase
import com.example.messages.data.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MessageViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).messageDao()

    private val _folder = MutableStateFlow("inbox")
    val folder: StateFlow<String> = _folder

    val messages: Flow<List<Message>> = _folder.flatMapLatest { dao.getByFolder(it) }

    fun setFolder(f: String) { _folder.value = f }

    fun save(message: Message) = viewModelScope.launch {
        if (message.id == 0L) dao.insert(message) else dao.update(message)
    }

    fun delete(message: Message) = viewModelScope.launch {
        dao.delete(message)
    }

    fun load(id: Long, onLoaded: (Message?) -> Unit) = viewModelScope.launch {
        onLoaded(dao.getById(id))
    }
}

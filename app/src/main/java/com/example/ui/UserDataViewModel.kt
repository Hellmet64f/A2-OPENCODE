package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.UiState
import com.example.data.UserDataRepository
import com.example.data.UserPreferencesDoc
import com.example.model.WatchHistoryItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserDataViewModel(
    private val userId: String,
    private val repository: UserDataRepository
) : ViewModel() {

    val historyState: StateFlow<UiState<List<WatchHistoryItem>>> = repository.observeHistory(userId)
        .map<List<WatchHistoryItem>, UiState<List<WatchHistoryItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.localizedMessage ?: "Falha ao sincronizar histórico"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val preferencesState: StateFlow<UiState<UserPreferencesDoc?>> = repository.observePreferences(userId)
        .map<UserPreferencesDoc?, UiState<UserPreferencesDoc?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.localizedMessage ?: "Falha ao ler preferências"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun saveHistoryItem(item: WatchHistoryItem) {
        viewModelScope.launch {
            repository.saveHistoryItem(item).onFailure { err ->
                _syncMessage.value = "Salvo localmente (nuvem pendente: ${err.localizedMessage ?: "offline"})"
            }
        }
    }

    fun syncLocalBatch(items: List<WatchHistoryItem>, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.syncHistoryBatch(items)
            _syncMessage.value = if (res.isSuccess) {
                "Dados sincronizados gratuitamente na nuvem!"
            } else {
                "Falha ao enviar para a nuvem: ${res.exceptionOrNull()?.localizedMessage ?: "tente novamente"}"
            }
            onDone(res.isSuccess)
        }
    }

    fun removeHistoryItem(itemUrl: String) {
        viewModelScope.launch {
            repository.removeHistoryItem(itemUrl)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun savePreferences(
        zoomPreset: String,
        zoomScale: Double,
        focusX: Double,
        focusY: Double,
        adBlockAutoUpdate: Boolean,
        recentSearches: List<String>
    ) {
        viewModelScope.launch {
            repository.savePreferences(
                zoomPreset = zoomPreset,
                zoomScale = zoomScale,
                focusX = focusX,
                focusY = focusY,
                adBlockAutoUpdate = adBlockAutoUpdate,
                recentSearches = recentSearches
            )
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    class Factory(
        private val userId: String,
        private val repository: UserDataRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UserDataViewModel(userId, repository) as T
        }
    }
}

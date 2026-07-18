package ps.palpay.tracker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ps.palpay.tracker.data.repository.TransactionRepository
import ps.palpay.tracker.domain.model.AppStatus

class MainViewModel(
    application: Application,
    private val repository: TransactionRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppStatus())
    val uiState: StateFlow<AppStatus> = _uiState.asStateFlow()

    // تحويل الـ Flow إلى StateFlow ساخن (Hot) لمنع إعادة الجلب عند الرجوع للشاشة
    val allTransactions: StateFlow<List<ps.palpay.tracker.data.local.db.TransactionEntity>> = repository.getAllTransactions()
        .map { it.take(50) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        observeRepositoryState()
    }

    private fun observeRepositoryState() {
        combine(
            repository.isListenerConnected,
            repository.isTrackingEnabled,
            repository.getTodayTransactions()
        ) { connected, tracking, transactions ->
            // إجراء الحسابات في خلفية الـ Flow وليس على الخيط الرئيسي مباشرة
            val palpayList = transactions.filter { it.walletSource == "PalPay" }
            val jawwalList = transactions.filter { it.walletSource == "JawwalPay" }
            
            AppStatus(
                isReady = true,
                listenerConnected = connected,
                trackingEnabled = tracking,
                palpayCount = palpayList.size,
                palpayTotal = palpayList.sumOf { it.amount },
                jawwalPayCount = jawwalList.size,
                jawwalPayTotal = jawwalList.sumOf { it.amount },
                batteryOptimizationIgnored = isBatteryOptimizationIgnored()
            )
        }
        .flowOn(Dispatchers.Default) // ضمان أن عمليات الـ filter والـ sumOf تتم بعيداً عن خيط الواجهة
        .onEach { status ->
            _uiState.value = status
        }
        .launchIn(viewModelScope)
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    fun toggleTracking(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setTrackingEnabled(enabled)
        }
    }

    fun refreshBatteryStatus() {
        viewModelScope.launch(Dispatchers.Default) {
            val isIgnored = isBatteryOptimizationIgnored()
            _uiState.update { it.copy(batteryOptimizationIgnored = isIgnored) }
        }
    }

    class Factory(private val application: Application, private val repository: TransactionRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
    }
}

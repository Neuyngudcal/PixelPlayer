package com.theveloper.pixelplay.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.ai.offline.BenchmarkStepState
import com.theveloper.pixelplay.data.ai.offline.CustomImportState
import com.theveloper.pixelplay.data.ai.offline.CustomModelImporter
import com.theveloper.pixelplay.data.ai.offline.DeviceHardwareInspector
import com.theveloper.pixelplay.data.ai.offline.DeviceHardwareSnapshot
import com.theveloper.pixelplay.data.ai.offline.LlamaEngine
import com.theveloper.pixelplay.data.ai.offline.ModelBenchmarkRunner
import com.theveloper.pixelplay.data.ai.offline.ModelDownloadState
import com.theveloper.pixelplay.data.ai.offline.OfflineModelCatalog
import com.theveloper.pixelplay.data.ai.offline.OfflineModelDownloadManager
import com.theveloper.pixelplay.data.ai.offline.OfflineModelInfo
import com.theveloper.pixelplay.data.ai.offline.StaticHardwareEvalResult
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OfflineModelHubViewModel @Inject constructor(
    private val hardwareInspector: DeviceHardwareInspector,
    private val downloadManager: OfflineModelDownloadManager,
    private val customModelImporter: CustomModelImporter,
    private val benchmarkRunner: ModelBenchmarkRunner,
    private val llamaEngine: LlamaEngine,
    private val preferencesRepo: AiPreferencesRepository
) : ViewModel() {

    private val _hardwareSnapshot = MutableStateFlow<DeviceHardwareSnapshot?>(null)
    val hardwareSnapshot: StateFlow<DeviceHardwareSnapshot?> = _hardwareSnapshot.asStateFlow()

    private val _customModels = MutableStateFlow<List<OfflineModelInfo>>(emptyList())
    val customModels: StateFlow<List<OfflineModelInfo>> = _customModels.asStateFlow()

    private val _customImportState = MutableStateFlow<CustomImportState>(CustomImportState.Idle)
    val customImportState: StateFlow<CustomImportState> = _customImportState.asStateFlow()

    private val _selectedModelForCheck = MutableStateFlow<OfflineModelInfo?>(null)
    val selectedModelForCheck: StateFlow<OfflineModelInfo?> = _selectedModelForCheck.asStateFlow()

    private val _benchmarkState = MutableStateFlow<BenchmarkStepState>(BenchmarkStepState.Idle)
    val benchmarkState: StateFlow<BenchmarkStepState> = _benchmarkState.asStateFlow()

    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = downloadManager.downloadStates

    val recommendedModels: StateFlow<List<OfflineModelInfo>> = downloadStates
        .combine(_hardwareSnapshot) { states, _ ->
            OfflineModelCatalog.recommendedModels.map { model ->
                val state = states[model.id]
                val downloadedFile = downloadManager.getDownloadedFile(model)
                val isDone = state is ModelDownloadState.Completed || downloadedFile != null
                model.copy(
                    isDownloaded = isDone,
                    localFilePath = (state as? ModelDownloadState.Completed)?.filePath
                        ?: downloadedFile?.absolutePath
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OfflineModelCatalog.recommendedModels
        )

    val activeOfflineModelId: StateFlow<String> = preferencesRepo.activeOfflineModelId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val activeOfflineModelName: StateFlow<String> = preferencesRepo.activeOfflineModelName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val activeOfflineModelPath: StateFlow<String> = preferencesRepo.activeOfflineModelPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val offlineHardwarePassed: StateFlow<Boolean> = preferencesRepo.offlineHardwarePassed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val offlineThreadsCount: StateFlow<Int> = preferencesRepo.offlineThreadsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val offlineUseGpu: StateFlow<Boolean> = preferencesRepo.offlineUseGpu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val offlineLastBenchmarkSpeed: StateFlow<Float> = preferencesRepo.offlineLastBenchmarkSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val isModelLoadedInRam: StateFlow<Boolean> = llamaEngine.isModelLoadedInRam

    private var benchmarkJob: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _hardwareSnapshot.value = withContext(Dispatchers.IO) {
                hardwareInspector.inspect()
            }
            downloadManager.refreshExistingDownloads()
            _customModels.value = customModelImporter.listCustomModels()
        }
    }

    fun evaluateStaticModelStatus(model: OfflineModelInfo): StaticHardwareEvalResult {
        return hardwareInspector.evaluateForModel(
            model = model,
            requireDownloadStorage = false
        )
    }

    fun startOrResumeDownload(model: OfflineModelInfo) {
        downloadManager.startOrResumeDownload(model)
    }

    fun pauseDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    fun deleteModel(model: OfflineModelInfo) {
        viewModelScope.launch {
            if (activeOfflineModelId.value == model.id || activeOfflineModelPath.value == model.localFilePath) {
                llamaEngine.unload()
                preferencesRepo.clearActiveOfflineModel()
            }
            if (model.isCustom) {
                customModelImporter.deleteCustomModel(model)
                _customModels.value = customModelImporter.listCustomModels()
            } else {
                downloadManager.cancelAndDeleteModel(model)
            }
            _hardwareSnapshot.value = withContext(Dispatchers.IO) {
                hardwareInspector.inspect()
            }
        }
    }

    fun importCustomGguf(uri: Uri) {
        viewModelScope.launch {
            customModelImporter.importGgufFromUri(uri).collect { state ->
                _customImportState.value = state
                if (state is CustomImportState.Completed) {
                    _customModels.value = customModelImporter.listCustomModels()
                    _hardwareSnapshot.value = withContext(Dispatchers.IO) {
                        hardwareInspector.inspect()
                    }
                    // Immediately launch mandatory Hardware Check + Dry-Run Dialog for the imported model
                    openHardwareCheckDialog(state.model)
                }
            }
        }
    }

    fun clearImportState() {
        _customImportState.value = CustomImportState.Idle
    }

    fun openHardwareCheckDialog(model: OfflineModelInfo) {
        _selectedModelForCheck.value = model
        startBenchmarkRun(model, userAcceptedRisk = false)
    }

    fun confirmRiskAndRunDryRun() {
        val model = _selectedModelForCheck.value ?: return
        startBenchmarkRun(model, userAcceptedRisk = true)
    }

    private fun startBenchmarkRun(model: OfflineModelInfo, userAcceptedRisk: Boolean) {
        benchmarkJob?.cancel()
        benchmarkJob = viewModelScope.launch {
            val threads = offlineThreadsCount.value
            val useGpu = offlineUseGpu.value
            benchmarkRunner.runHardwareCheckAndBenchmark(
                model = model,
                userAcceptedWarningRisk = userAcceptedRisk,
                threadsCount = threads,
                useGpu = useGpu
            ).collect { stepState ->
                _benchmarkState.value = stepState
            }
        }
    }

    fun activateVerifiedModel(model: OfflineModelInfo, benchmarkSpeed: Float) {
        val path = model.localFilePath ?: return
        viewModelScope.launch {
            llamaEngine.unload()
            preferencesRepo.setActiveOfflineModel(
                modelId = model.id,
                modelName = model.displayName,
                modelPath = path,
                hardwarePassed = true,
                benchmarkSpeedTokensPerSec = benchmarkSpeed
            )
            preferencesRepo.setModel(AiProvider.OFFLINE_GGUF, model.fileName)
            preferencesRepo.setAiProvider(AiProvider.OFFLINE_GGUF.name)
            dismissHardwareDialog()
        }
    }

    fun dismissHardwareDialog() {
        benchmarkJob?.cancel()
        _selectedModelForCheck.value = null
        _benchmarkState.value = BenchmarkStepState.Idle
    }

    fun setThreadsCount(threads: Int) {
        viewModelScope.launch {
            preferencesRepo.setOfflineThreadsCount(threads)
        }
    }

    fun setUseGpu(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setOfflineUseGpu(enabled)
        }
    }

    fun unloadModelFromRamNow() {
        viewModelScope.launch {
            llamaEngine.unload()
            _hardwareSnapshot.value = withContext(Dispatchers.IO) {
                hardwareInspector.inspect()
            }
        }
    }
}

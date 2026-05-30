package com.zva.agent.ui.screen.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zva.agent.data.db.MemoryEntity
import com.zva.agent.domain.agent.AppSettings
import com.zva.agent.domain.agent.PersonaManager
import com.zva.agent.domain.memory.MemoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeViewModel @Inject constructor(
    private val personaManager: PersonaManager,
    private val memoryManager: MemoryManager,
) : ViewModel() {

    val settings: Flow<AppSettings> = personaManager.settings

    val memories: Flow<List<MemoryEntity>> = memoryManager.getAllMemories()

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            personaManager.save(settings)
        }
    }
}

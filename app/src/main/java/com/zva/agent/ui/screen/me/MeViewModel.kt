package com.zva.agent.ui.screen.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zva.agent.data.db.*
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
    private val personaDao: PersonaDao,
    private val subAgentDao: SubAgentDao,
    private val memoryDao: MemoryDao,
) : ViewModel() {

    val settings: Flow<AppSettings> = personaManager.settings
    val memories: Flow<List<MemoryEntity>> = memoryManager.getAllMemories()
    val personas: Flow<List<PersonaEntity>> = personaDao.getAll()
    val subAgents: Flow<List<SubAgentEntity>> = subAgentDao.getAll()

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch { personaManager.save(settings) }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch { memoryDao.delete(memory) }
    }

    fun addPersona(name: String, emoji: String, prompt: String, temperature: Float) {
        viewModelScope.launch {
            personaDao.insert(PersonaEntity(name = name, emoji = emoji, systemPrompt = prompt, temperature = temperature))
        }
    }

    fun deletePersona(persona: PersonaEntity) {
        viewModelScope.launch { personaDao.delete(persona) }
    }

    fun setDefaultPersona(id: Long) {
        viewModelScope.launch {
            personaDao.clearDefaults()
            personaDao.setDefault(id)
        }
    }

    fun addSubAgent(name: String, role: String, prompt: String) {
        viewModelScope.launch {
            subAgentDao.insert(SubAgentEntity(name = name, role = role, systemPrompt = prompt))
        }
    }

    fun deactivateSubAgent(agent: SubAgentEntity) {
        viewModelScope.launch { subAgentDao.deactivate(agent.id) }
    }
}

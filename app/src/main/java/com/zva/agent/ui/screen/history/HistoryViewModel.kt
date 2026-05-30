package com.zva.agent.ui.screen.history

import androidx.lifecycle.ViewModel
import com.zva.agent.data.db.SessionDao
import com.zva.agent.data.db.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionDao: SessionDao,
) : ViewModel() {

    val sessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()
}

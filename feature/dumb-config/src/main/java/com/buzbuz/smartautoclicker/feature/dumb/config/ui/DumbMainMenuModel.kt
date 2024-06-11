/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine
import com.buzbuz.smartautoclicker.feature.dumb.config.domain.DumbEditionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DumbMainMenuModel @Inject constructor(
    private val dumbEditionRepository: DumbEditionRepository,
    private val dumbEngine: DumbEngine,
) : ViewModel() {
    private val TAG = "hz:DumbMainMenuModel"
    val canPlay: Flow<Boolean> =
        combine(dumbEditionRepository.isEditionSynchronized, dumbEngine.dumbScenario) { isSync, scenario ->
            isSync && scenario?.isValid() ?: false
        }
    val isPlaying: StateFlow<Boolean> =
        dumbEngine.isRunning

    fun startEdition(dumbScenarioId: Identifier, onStarted: () -> Unit) {

        Log.d(TAG, "startEdition.....")


        viewModelScope.launch(Dispatchers.IO) {
            if (dumbEditionRepository.startEdition(dumbScenarioId.databaseId)) {
                withContext(Dispatchers.Main) {
                    onStarted()
                }
            }
        }
    }

    fun saveEditions() {
        Log.d(TAG, "saveEditions.....")

        viewModelScope.launch(Dispatchers.IO) {
            dumbEditionRepository.apply {
                saveEditions()
            }
        }
    }

    fun stopEdition() {
        Log.d(TAG, "stopEdition.....")

        dumbEditionRepository.stopEdition()
    }

    fun toggleScenarioPlay() {

        Log.d(TAG, "toggleScenarioPlay.....")

        viewModelScope.launch {
            if (isPlaying.value) dumbEngine.stopDumbScenario()
            else dumbEngine.startDumbScenario()
        }
    }

    fun stopScenarioPlay(): Boolean {

        Log.d(TAG, "stopScenarioPlay.....")

        if (!isPlaying.value) return false

        viewModelScope.launch {
            dumbEngine.stopDumbScenario()
        }
        return true
    }
}
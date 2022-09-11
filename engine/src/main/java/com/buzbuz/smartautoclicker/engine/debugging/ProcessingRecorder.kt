/*
 * Copyright (C) 2022 Nain57
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.engine.debugging

import kotlin.math.max
import kotlin.math.min

/** Record the processing of an event. */
internal class ProcessingRecorder {

    /** The time in milliseconds of the last call to [onProcessingStart]. */
    private var currentRecordStartTimeMs: Long = INVALID_TIME_VALUE
    /** The total processing time of all events. */
    private var totalTimeMs: Long = 0L
    /** The minimum processing time of an event. */
    private var minTimeMs: Long = Long.MAX_VALUE
    /** The maximum processing time of an event. */
    private var maxTimeMs: Long = Long.MIN_VALUE
    /** The number of event processed. */
    private var count: Long = 0L
    /** The number of event processed with a success. */
    var successCount: Long = 0L
        private set

    fun onProcessingStart() {
        currentRecordStartTimeMs = System.currentTimeMillis()
    }

    fun onProcessingEnd(success: Boolean = true) {
        val processingDurationMs =  System.currentTimeMillis() - currentRecordStartTimeMs
        currentRecordStartTimeMs = INVALID_TIME_VALUE

        count++
        if (success) successCount++

        totalTimeMs += processingDurationMs
        minTimeMs = min(processingDurationMs, minTimeMs)
        maxTimeMs = max(processingDurationMs, maxTimeMs)
    }

    fun toProcessingDebugInfo() = ProcessingDebugInfo(
        processingCount = count,
        successCount = successCount,
        totalProcessingTimeMs = totalTimeMs,
        avgProcessingTimeMs = if (count != 0L) totalTimeMs / count else 0L,
        minProcessingTimeMs = minTimeMs,
        maxProcessingTimeMs = maxTimeMs,
    )
}

internal const val INVALID_TIME_VALUE = -1L
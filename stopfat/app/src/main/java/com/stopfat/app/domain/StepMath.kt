package com.stopfat.app.domain

import com.stopfat.app.data.ProfileStore.StepState

/**
 * Converts the phone's cumulative TYPE_STEP_COUNTER value (steps since last reboot)
 * into "steps today", handling day changes and device reboots.
 */
object StepMath {

    data class Result(val state: StepState, val todaySteps: Int)

    fun update(state: StepState, counter: Long, today: Long): Result {
        val newState = when {
            // First reading ever – start counting from now.
            state.baselineDay < 0 || state.lastCounter < 0 -> StepState(counter, today, counter, today)

            // New day.
            state.baselineDay != today -> {
                val baseline = when {
                    counter < state.lastCounter -> 0L // rebooted since the last reading
                    state.lastDay == today - 1 -> state.lastCounter // steps after the last reading of yesterday count for today
                    else -> counter
                }
                StepState(baseline, today, counter, today)
            }

            // Same day, but the counter went down – the phone was rebooted.
            counter < state.lastCounter -> {
                val stepsSoFar = state.lastCounter - state.baseline
                StepState(-stepsSoFar, today, counter, today) // counter restarted from 0 at boot
            }

            else -> state.copy(lastCounter = counter, lastDay = today)
        }
        val steps = (counter - newState.baseline).coerceIn(0, 200_000).toInt()
        return Result(newState, steps)
    }
}

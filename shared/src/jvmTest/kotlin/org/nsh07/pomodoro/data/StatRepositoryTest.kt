/*
 * Copyright (c) 2026 Nishant Mishra
 *
 * This file is part of Tomato - a minimalist pomodoro timer for Android.
 *
 * Tomato is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tomato is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tomato.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package org.nsh07.pomodoro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Tests for [AppStatRepository], which records a session against today's row for one topic. */
class StatRepositoryTest : DatabaseTest() {

    private val repository by lazy { AppStatRepository(statDao, Dispatchers.IO) }

    @Test
    fun `focus time is recorded against one topic at a time`(): Unit = runBlocking {
        val work = insertTopic("Work")
        val reading = insertTopic("Reading")

        repository.addFocusTime(work.id, 1000)
        repository.addFocusTime(work.id, 500)
        repository.addFocusTime(reading.id, 250)
        repository.addBreakTime(reading.id, 75)

        assertEquals(1500L, topicDao.getStats(work.id).single().totalFocusTime())
        assertEquals(0L, topicDao.getStats(work.id).single().breakTime)
        assertEquals(250L, topicDao.getStats(reading.id).single().totalFocusTime())
        assertEquals(75L, topicDao.getStats(reading.id).single().breakTime)
    }

    @Test
    fun `focus time still lands on a day that was seeded empty first`(): Unit = runBlocking {
        val work = insertTopic("Work")

        statDao.insertStat(Stat(LocalDate.now(), work.id, 0, 0, 0, 0, 0))
        repository.addFocusTime(work.id, 1000)
        repository.addBreakTime(work.id, 250)

        val today = topicDao.getStats(work.id).single()
        assertEquals(1000L, today.totalFocusTime())
        assertEquals(250L, today.breakTime)
    }

    @Test
    fun `all of a session's focus time lands in a single quarter`(): Unit = runBlocking {
        val work = insertTopic("Work")

        repository.addFocusTime(work.id, 1000)

        val today = topicDao.getStats(work.id).single()
        val quarters = listOf(
            today.focusTimeQ1,
            today.focusTimeQ2,
            today.focusTimeQ3,
            today.focusTimeQ4
        )
        assertEquals(listOf(1000L), quarters.filter { it != 0L })
    }

    @Test
    fun `break time is kept out of the focus quarters`(): Unit = runBlocking {
        val work = insertTopic("Work")

        repository.addBreakTime(work.id, 500)

        val today = topicDao.getStats(work.id).single()
        assertEquals(0L, today.totalFocusTime())
        assertEquals(500L, today.breakTime)
    }

    @Test
    fun `getThisWeekStat accumulates current week and resets on Sunday`(): Unit = runBlocking {
        val work = insertTopic("Work")
        val today = LocalDate.now()
        val daysSinceSunday = (today.dayOfWeek.value % 7).toLong()
        val startOfWeek = today.minusDays(daysSinceSunday)
        val priorToSunday = startOfWeek.minusDays(1)

        // Stat before Sunday (previous week)
        statDao.insertStat(Stat(priorToSunday, work.id, 9999, 0, 0, 0, 100))

        // Stat on Sunday (start of current week)
        statDao.insertStat(Stat(startOfWeek, work.id, 2000, 0, 0, 0, 50))

        // Stat today (if today != startOfWeek, insert today as well)
        if (today != startOfWeek) {
            statDao.insertStat(Stat(today, work.id, 3000, 0, 0, 0, 75))
        }

        val weekStat = assertNotNull(repository.getThisWeekStat().first())
        val expectedFocus = if (today != startOfWeek) 5000L else 2000L
        val expectedBreak = if (today != startOfWeek) 125L else 50L
        assertEquals(expectedFocus, weekStat.totalFocusTime())
        assertEquals(expectedBreak, weekStat.breakTime)
    }
}

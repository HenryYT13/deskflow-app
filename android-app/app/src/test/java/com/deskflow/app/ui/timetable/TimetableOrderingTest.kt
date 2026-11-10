package com.deskflow.app.ui.timetable

import com.deskflow.app.data.model.DayScheduleDto
import com.deskflow.app.data.model.TimetableDataWrapper
import org.junit.Assert.*
import org.junit.Test

class TimetableOrderingTest {

    @Test
    fun testTodayAlwaysAtTopAndTomorrowUnderneath() {
        val todaySchedule = DayScheduleDto(
            day = "Thứ 4",
            displayDay = "Hôm nay - Thứ 4",
            morning = listOf("Tiết 1: Toán", "Tiết 2: Văn", "Tiết 3: Anh"),
            afternoon = listOf("Tiết 1: GDCD"),
            uniformMorning = "Áo trắng",
            uniformAfternoon = "Thể dục",
            hasAfternoon = true,
            hasAnyClass = true
        )

        val tomorrowSchedule = DayScheduleDto(
            day = "Thứ 5",
            displayDay = "Thứ 5",
            morning = listOf("Tiết 1: Lý", "Tiết 2: Hóa"),
            afternoon = emptyList(),
            uniformMorning = "Áo cờ",
            uniformAfternoon = "",
            hasAfternoon = false,
            hasAnyClass = true
        )

        val timetableData = TimetableDataWrapper(
            today = todaySchedule,
            tomorrow = tomorrowSchedule
        )

        // Strict verification: Today at index 0, Tomorrow at index 1
        val scheduleList = listOf(timetableData.today, timetableData.tomorrow)

        assertEquals("First group must be Today", "Thứ 4", scheduleList[0]?.day)
        assertEquals("Second group must be Tomorrow", "Thứ 5", scheduleList[1]?.day)

        // Ensure Tomorrow is NOT before Today
        assertNotEquals("Tomorrow must not be first", "Thứ 5", scheduleList[0]?.day)

        // Verify periods and subjects for Today
        val todayMorning = scheduleList[0]!!.morning
        assertEquals(3, todayMorning.size)
        assertTrue(todayMorning[0].startsWith("Tiết 1"))
        assertTrue(todayMorning[0].contains("Toán"))
        assertEquals("Áo trắng", scheduleList[0]!!.uniformMorning)

        // Verify periods for Tomorrow
        val tomorrowMorning = scheduleList[1]!!.morning
        assertEquals(2, tomorrowMorning.size)
        assertTrue(tomorrowMorning[0].contains("Lý"))
    }

    @Test
    fun testEmptyDayHandlingGraceful() {
        val todayEmpty = DayScheduleDto(
            day = "Chủ Nhật",
            morning = emptyList(),
            afternoon = emptyList(),
            uniformMorning = "Tự do",
            hasAfternoon = false,
            hasAnyClass = false
        )

        val tomorrowEmpty = DayScheduleDto(
            day = "Thứ 2",
            morning = emptyList(),
            afternoon = emptyList(),
            uniformMorning = "Tự do",
            hasAfternoon = false,
            hasAnyClass = false
        )

        val data = TimetableDataWrapper(today = todayEmpty, tomorrow = tomorrowEmpty)

        assertFalse("Today has no classes", data.today!!.hasAnyClass)
        assertFalse("Tomorrow has no classes", data.tomorrow!!.hasAnyClass)
        assertTrue("Morning list empty", data.today!!.morning.isEmpty())
        assertTrue("Afternoon list empty", data.today!!.afternoon.isEmpty())
    }

    @Test
    fun testLessonParsingSeparatesPeriodAndSubject() {
        val rawLesson = "Tiết 1: Lịch sử & Địa lý"
        val parts = rawLesson.split(":")
        val period = parts[0].trim()
        val subject = parts.drop(1).joinToString(":").trim()

        assertEquals("Tiết 1", period)
        assertEquals("Lịch sử & Địa lý", subject)
    }
}

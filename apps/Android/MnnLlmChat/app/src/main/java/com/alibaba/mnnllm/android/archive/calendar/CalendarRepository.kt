package com.alibaba.mnnllm.android.archive.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Writes archive records to the system calendar via CalendarProvider (PRD module 6).
 * Event note stores the archive id for two-way traceability.
 */
object CalendarRepository {

    private const val TAG = "CalendarRepository"
    const val NOTE_PREFIX = "archive_id://"
    private val FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /** Reminder presets in minutes (PRD: 30min / 1h / 1d / 3d). */
    val REMINDER_PRESETS = linkedMapOf(
        "30分钟前" to 30,
        "1小时前" to 60,
        "1天前" to 24 * 60,
        "3天前" to 3 * 24 * 60
    )

    fun parseDeadline(deadline: String?): Long? {
        if (deadline.isNullOrBlank()) return null
        return try { FMT.parse(deadline)?.time } catch (e: Exception) {
            Log.w(TAG, "parse deadline failed: $deadline"); null
        }
    }

    private fun getPrimaryCalendarId(context: Context): Long? {
        val proj = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, proj, null, null, null
        )?.use { c ->
            var firstId: Long? = null
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (firstId == null) firstId = id
                if (c.getInt(1) == 1) return id
            }
            return firstId
        }
        return null
    }

    /**
     * Inserts an event. Returns the event id, or null on failure.
     * @param reminderMinutes minutes-before reminder (one of REMINDER_PRESETS values)
     */
    fun insertEvent(
        context: Context,
        archiveId: Long,
        title: String,
        deadline: String?,
        location: String?,
        reminderMinutes: Int?
    ): Long? {
        val startMs = parseDeadline(deadline) ?: return null
        val calId = getPrimaryCalendarId(context) ?: run {
            Log.w(TAG, "no calendar found"); return null
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, startMs + 60 * 60 * 1000) // 1h default
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, "$NOTE_PREFIX$archiveId")
            if (!location.isNullOrBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return null
        val eventId = ContentUris.parseId(uri)
        if (reminderMinutes != null) {
            val rv = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, rv)
        }
        Log.d(TAG, "inserted calendar event $eventId for archive $archiveId")
        return eventId
    }
}

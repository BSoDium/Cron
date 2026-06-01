package fr.bsodium.cron.session.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstructionTest {

    @Test
    fun doNothing_sets_action_and_leaves_alarm_fields_null() {
        val now = Instant.parse("2026-05-22T03:00:00Z")

        val instruction = Instruction.doNothing(reason = "no change needed", now = now)

        assertEquals(ActionType.DoNothing, instruction.action)
        assertEquals("no change needed", instruction.reason)
        assertEquals(now, instruction.issuedAt)
        assertNull(instruction.alarmTime)
        assertNull(instruction.wakeWindowStart)
        assertNull(instruction.wakeWindowEnd)
        assertNull(instruction.briefContent)
    }
}

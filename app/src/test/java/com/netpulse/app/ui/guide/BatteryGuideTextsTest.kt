package com.netpulse.app.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryGuideTextsTest {

    private fun allText(content: BatteryGuideContent): String =
        buildString {
            append(content.introTitle).append(' ')
            append(content.introBody).append(' ')
            content.steps.forEach { step ->
                append(step.title).append(' ')
                append(step.description).append(' ')
            }
        }

    @Test
    fun `generic guide never mentions samsung`() {
        val content = BatteryGuideTexts.forDevice(isSamsungDevice = false)
        assertFalse(content.isSamsungDevice)
        assertFalse(allText(content).contains("samsung", ignoreCase = true))
    }

    @Test
    fun `generic guide never mentions one ui or device care`() {
        val text = allText(BatteryGuideTexts.forDevice(isSamsungDevice = false))
        assertFalse(text.contains("One UI", ignoreCase = true))
        assertFalse(text.contains("Device Care", ignoreCase = true))
        assertFalse(text.contains("Deep Sleeping", ignoreCase = true))
    }

    @Test
    fun `samsung guide keeps samsung specific copy`() {
        val content = BatteryGuideTexts.forDevice(isSamsungDevice = true)
        assertTrue(content.isSamsungDevice)
        assertTrue(content.introTitle.contains("Samsung"))
        assertTrue(allText(content).contains("Device Care"))
    }

    @Test
    fun `both guides expose three non-empty steps`() {
        listOf(true, false).forEach { samsung ->
            val content = BatteryGuideTexts.forDevice(samsung)
            assertEquals(3, content.steps.size)
            assertTrue(content.introTitle.isNotBlank())
            assertTrue(content.introBody.isNotBlank())
            content.steps.forEach { step ->
                assertTrue(step.title.isNotBlank())
                assertTrue(step.description.isNotBlank())
            }
        }
    }
}

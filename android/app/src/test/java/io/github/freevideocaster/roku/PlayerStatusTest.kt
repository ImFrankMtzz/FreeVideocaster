package io.github.freevideocaster.roku

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStatusTest {

    @Test
    fun aacAdtsIsNotMistakenForDts() {
        // Regresión: «aac_adts» contiene «dts» y avisaba por error.
        assertNull(PlayerStatus.audioWarningFor("aac_adts"))
    }

    @Test
    fun commonSafeCodecsDoNotWarn() {
        listOf("ac3", "eac3", "aac", "mp3", "flac", "opus", null).forEach {
            assertNull("«$it» no debería avisar", PlayerStatus.audioWarningFor(it))
        }
    }

    @Test
    fun dtsAndTrueHdWarn() {
        listOf("dts", "DTS", "dts_hd", "dts-hd", "dtshd", "truehd", "TrueHD").forEach {
            val warning = PlayerStatus.audioWarningFor(it)
            assertNotNull("«$it» debería avisar", warning)
            assertTrue(warning!!.contains(it.uppercase()))
        }
    }
}

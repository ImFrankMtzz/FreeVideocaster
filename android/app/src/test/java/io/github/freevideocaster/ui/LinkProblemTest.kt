package io.github.freevideocaster.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkProblemTest {

    @Test
    fun validLinksHaveNoProblem() {
        listOf("https://cdn.example/v.mkv", "http://192.168.1.5/video.mp4", "  HTTPS://Example.com/a.m3u8  ").forEach {
            assertNull("«$it» debería valer", linkProblem(it))
        }
    }

    @Test
    fun emptyInputIsNotAnErrorYet() {
        assertNull(linkProblem(""))
        assertNull(linkProblem("   "))
    }

    @Test
    fun magnetLinksAreExplainedInPlainLanguage() {
        val message = linkProblem("magnet:?xt=urn:btih:abcdef")
        assertNotNull(message)
        assertTrue(message!!.contains("magnet"))
        assertTrue("debe orientar al usuario", message.contains("Stremio"))
    }

    @Test
    fun otherTextIsRejected() {
        assertNotNull(linkProblem("ftp://example.com/v.mp4"))
        assertNotNull(linkProblem("example.com/v.mp4"))
        assertNotNull(linkProblem("hola"))
    }
}

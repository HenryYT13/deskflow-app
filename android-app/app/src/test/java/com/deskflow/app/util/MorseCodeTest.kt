package com.deskflow.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseCodeTest {

    @Test
    fun testTextToMorse_singleLetters() {
        assertEquals(".-", MorseCode.textToMorse("A"))
        assertEquals("-...", MorseCode.textToMorse("B"))
        assertEquals(".", MorseCode.textToMorse("E"))
        assertEquals("-", MorseCode.textToMorse("T"))
        assertEquals("..-.", MorseCode.textToMorse("F"))
        assertEquals("--..", MorseCode.textToMorse("Z"))
    }

    @Test
    fun testTextToMorse_allAlphabet() {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val morse = MorseCode.textToMorse(alphabet)
        val decoded = MorseCode.morseToText(morse)
        assertTrue(decoded.isValid)
        assertEquals(alphabet, decoded.text)
    }

    @Test
    fun testTextToMorse_caseInsensitive() {
        assertEquals("... --- ...", MorseCode.textToMorse("SOS"))
        assertEquals("... --- ...", MorseCode.textToMorse("sos"))
        assertEquals("... --- ...", MorseCode.textToMorse("SoS"))
    }

    @Test
    fun testTextToMorse_numbers() {
        val result = MorseCode.textToMorse("0123456789")
        assertEquals("----- .---- ..--- ...-- ....- ..... -.... --... ---.. ----.", result)
        val decoded = MorseCode.morseToText(result)
        assertTrue(decoded.isValid)
        assertEquals("0123456789", decoded.text)
    }

    @Test
    fun testTextToMorse_punctuation() {
        val punct = ".,?/-()=+@!:;\"'_&"
        val morse = MorseCode.textToMorse(punct)
        val decoded = MorseCode.morseToText(morse)
        assertTrue(decoded.isValid)
        assertEquals(punct, decoded.text)
    }

    @Test
    fun testTextToMorse_multipleWordsAndSpaces() {
        val result = MorseCode.textToMorse("HELLO   WORLD  DESKFLOW")
        assertEquals(".... . .-.. .-.. --- / .-- --- .-. .-.. -.. / -.. . ... -.- ..-. .-.. --- .--", result)
        val decoded = MorseCode.morseToText(result)
        assertTrue(decoded.isValid)
        assertEquals("HELLO WORLD DESKFLOW", decoded.text)
    }

    @Test
    fun testTextToMorse_emptyAndBlank() {
        assertEquals("", MorseCode.textToMorse(""))
        assertEquals("", MorseCode.textToMorse("   "))
        assertEquals("", MorseCode.textToMorse("\t\n"))
    }

    @Test
    fun testTextToMorse_longInput() {
        val longText = "SOS ".repeat(50).trim()
        val morse = MorseCode.textToMorse(longText)
        assertTrue(morse.length > 500)
        val decoded = MorseCode.morseToText(morse)
        assertTrue(decoded.isValid)
        assertEquals(longText, decoded.text)
    }

    @Test
    fun testTextToMorse_unsupportedCharactersIgnoredCleanly() {
        // Accents or non-ITU characters dropped without crashing
        val result = MorseCode.textToMorse("HELLO #$* WORLD")
        val decoded = MorseCode.morseToText(result)
        assertTrue(decoded.isValid)
        assertEquals("HELLO WORLD", decoded.text)
    }

    @Test
    fun testMorseToText_valid() {
        val res = MorseCode.morseToText("... --- ...")
        assertTrue(res.isValid)
        assertEquals("SOS", res.text)
    }

    @Test
    fun testMorseToText_multipleWords() {
        val res = MorseCode.morseToText(".... . .-.. .-.. --- / .-- --- .-. .-.. -..")
        assertTrue(res.isValid)
        assertEquals("HELLO WORLD", res.text)
    }

    @Test
    fun testMorseToText_withPipeSeparator() {
        val res = MorseCode.morseToText(".... . .-.. .-.. --- | .-- --- .-. .-.. -..")
        assertTrue(res.isValid)
        assertEquals("HELLO WORLD", res.text)
    }

    @Test
    fun testMorseToText_withMultipleSpacesWordSeparator() {
        val res = MorseCode.morseToText(".... . .-.. .-.. ---    .-- --- .-. .-.. -..")
        assertTrue(res.isValid)
        assertEquals("HELLO WORLD", res.text)
    }

    @Test
    fun testMorseToText_withLeadingAndTrailingSpaces() {
        val res = MorseCode.morseToText("   ... --- ...   ")
        assertTrue(res.isValid)
        assertEquals("SOS", res.text)
    }

    @Test
    fun testMorseToText_invalidToken() {
        val res = MorseCode.morseToText("... --- .......")
        assertFalse(res.isValid)
        assertTrue(res.errorMessage != null)
        assertTrue(res.errorMessage!!.contains("......."))
        assertEquals("", res.text)
    }

    @Test
    fun testMorseToText_invalidCharacters() {
        val res = MorseCode.morseToText("... --- ... #?")
        assertFalse(res.isValid)
        assertTrue(res.errorMessage != null)
        assertTrue(res.errorMessage!!.contains("#"))
    }

    @Test
    fun testMorseToText_emptyAndBlank() {
        val res1 = MorseCode.morseToText("")
        assertTrue(res1.isValid)
        assertEquals("", res1.text)

        val res2 = MorseCode.morseToText("   ")
        assertTrue(res2.isValid)
        assertEquals("", res2.text)
    }

    @Test
    fun testValidateMorse() {
        val (isValid1, err1) = MorseCode.validateMorse("... --- ... / ...")
        assertTrue(isValid1)
        assertEquals(null, err1)

        val (isValid2, err2) = MorseCode.validateMorse(".- .- | .-")
        assertTrue(isValid2)
        assertEquals(null, err2)

        val (isInvalid, err) = MorseCode.validateMorse("... --- SOS #123")
        assertFalse(isInvalid)
        assertTrue(err != null)
    }

    @Test
    fun testNormalizeForHardware() {
        val normalized1 = MorseCode.normalizeForHardware("  ... --- ...  |  .-  ")
        assertEquals("... --- ... / .-", normalized1)

        val normalized2 = MorseCode.normalizeForHardware("... --- ...   .-")
        assertEquals("... --- ... / .-", normalized2)
    }
}

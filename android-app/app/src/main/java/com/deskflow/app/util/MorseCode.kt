package com.deskflow.app.util

/**
 * Standard International Morse Code Converter & Validator
 * Supports ITU standard characters: A-Z, 0-9, and standard spacing.
 */
object MorseCode {

    private val CHAR_TO_MORSE: Map<Char, String> = mapOf(
        'A' to ".-",    'B' to "-...",  'C' to "-.-.",  'D' to "-..",
        'E' to ".",     'F' to "..-.",  'G' to "--.",   'H' to "....",
        'I' to "..",    'J' to ".---",  'K' to "-.-",   'L' to ".-..",
        'M' to "--",    'N' to "-.",    'O' to "---",   'P' to ".--.",
        'Q' to "--.-",  'R' to ".-.",   'S' to "...",   'T' to "-",
        'U' to "..-",   'V' to "...-",  'W' to ".--",   'X' to "-..-",
        'Y' to "-.--",  'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.",
        '-' to "-....-", '(' to "-.--.",  ')' to "-.--.-", '=' to "-...-",
        '+' to ".-.-.",  '@' to ".--.-.", '!' to "-.-.--", ':' to "---...",
        ';' to "-.-.-.", '"' to ".-..-.", '\'' to ".----.", '_' to "..--.-",
        '&' to ".-..."
    )

    private val MORSE_TO_CHAR: Map<String, Char> = CHAR_TO_MORSE.entries.associate { (k, v) -> v to k }

    data class DecodeResult(
        val text: String,
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Converts plain text to International Morse Code.
     * Letters within a word are separated by a single space.
     * Words are separated by " / ".
     */
    fun textToMorse(input: String): String {
        if (input.isBlank()) return ""

        val words = input.trim().split(Regex("\\s+"))
        return words.joinToString(separator = " / ") { word ->
            word.uppercase().mapNotNull { ch ->
                CHAR_TO_MORSE[ch]
            }.joinToString(separator = " ")
        }
    }

    /**
     * Validates whether a string contains only valid Morse code symbols (., -, /, |, space, newline).
     */
    fun validateMorse(morseInput: String): Pair<Boolean, String?> {
        val trimmed = morseInput.trim()
        if (trimmed.isEmpty()) {
            return Pair(true, null)
        }

        val invalidChars = trimmed.filter { ch ->
            ch != '.' && ch != '-' && ch != ' ' && ch != '/' && ch != '|' && ch != '\n' && ch != '\r'
        }.toSet()

        if (invalidChars.isNotEmpty()) {
            val list = invalidChars.joinToString("', '") { it.toString() }
            return Pair(false, "Ký tự không hợp lệ: '$list'. Chỉ dùng dấu chấm (.), gạch (-), cách và gạch chéo (/).")
        }

        // Validate each token
        val words = trimmed.split(Regex("[/|]"))
        for (word in words) {
            val letters = word.trim().split(Regex("\\s+"))
            for (code in letters) {
                if (code.isNotEmpty() && !MORSE_TO_CHAR.containsKey(code)) {
                    return Pair(false, "Mã Morse không xác định: '$code'.")
                }
            }
        }

        return Pair(true, null)
    }

    /**
     * Converts Morse code back to plain text.
     * Understands single space as letter separator, and "/" or multiple spaces as word separator.
     */
    fun morseToText(morseInput: String): DecodeResult {
        val trimmed = morseInput.trim()
        if (trimmed.isEmpty()) {
            return DecodeResult(text = "", isValid = true)
        }

        val (valid, errorMsg) = validateMorse(trimmed)
        if (!valid) {
            return DecodeResult(text = "", isValid = false, errorMessage = errorMsg)
        }

        // Split words by '/' or '|' or multiple spaces (3 or more)
        val normalized = trimmed.replace(Regex("[/|]"), " / ")
        val rawWords = normalized.split(Regex("\\s{3,}|\\s*/\\s*"))

        val decodedWords = rawWords
            .filter { it.isNotBlank() }
            .map { wordSegment ->
                val letters = wordSegment.trim().split(Regex("\\s+"))
                letters.mapNotNull { code ->
                    if (code.isEmpty()) null else MORSE_TO_CHAR[code] ?: '?'
                }.joinToString("")
            }

        return DecodeResult(
            text = decodedWords.joinToString(" "),
            isValid = true,
            errorMessage = null
        )
    }

    /**
     * Normalizes Morse code for transmission to hardware:
     * Retains '.', '-', spaces, and converts word separators to '/'.
     */
    fun normalizeForHardware(morseInput: String): String {
        return morseInput.trim()
            .replace(Regex("[|]"), "/")
            .replace(Regex("\\s{3,}"), " / ")
            .replace(Regex("\\s+"), " ")
    }
}

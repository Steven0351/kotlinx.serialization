/*
 * Copyright 2017-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.test.assertSerializedAndRestored
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.test.assertTrue

/*
 * Test that checks JSON specification conformance based on "Parsing JSON is a Minefield".
 * Test resources are from: https://github.com/nst/JSONTestSuite
 *
 * Not all tests are present, because our parser is slightly different from the spec (well, as any other widespread Java JSON parser):
 * 1) We have relaxed quoting requirement. E.g. we accept "{"a": b, c: "d"}" as a valid JSON, while it is an invalid JSON
 * 2) Java has a much more powerful floating-point parser that accepts such floats as ".2"
 * 3) Incorrect UTF-8 symbols are automatically replaced with replacement char by Java's charset
 * 4) Raw literals are parsed as literals
 * 5) Single quotes are allowed (see p.1)
 * 6) New lines are supported
 */
class SpecConformanceTest {

    /*
     * List of incompatibilities (number cases are skipped for the sake of verbosity), parser should fail, but it does not:
     * ```
     * {a: "b"}, n_object_unquoted_key.json // p.1
     * [-2.], n_number_-2..json // p.2
     * [123�], n_number_invalid-utf-8-in-bigger-int.json // p.2
     * [a�], n_array_a_invalid_utf8.json // p.1 + p.3
     * [-], n_array_just_minus.json // p.1
     * ["	"], n_string_unescaped_tab.json // tab is just okay
     * aå, n_structure_ascii-unicode-identifier.json //  p.4
     * [é], n_string_accentuated_char_no_quotes.json // p.1
     * *, n_structure_single_star.json // p.4
     * �, n_structure_lone-invalid-utf-8.json // p.3 + p.4
     * {key: 'value'}, n_object_key_with_single_quotes.json // p.1
     * {1:1}, n_object_non_string_key.json // p.1
     * ﻿, n_structure_UTF8_BOM_no_data.json //  p.4
     * [⁠], n_structure_whitespace_U+2060_word_joiner.json // p.1 + p.3 + p.4
     * [True], n_structure_capitalized_True.json // p.1
     * [*], n_array_star_inside.json // p.1
     * [tru], n_incomplete_true.json // p.1
     * ["a a"], n_string_unescaped_crtl_char.json // p.3
     * [, n_structure_lone-open-bracket.json // p.1 + p.4
     * å, n_structure_unicode-identifier.json // p.1 + p.4
     * [nul], n_incomplete_null.json // p.1
     * [�], n_array_invalid_utf8.json // p.1 + p.3
     * ["x", truth], n_object_bad_value.json // p.1
     * {'a':0}, n_object_single_quote.json // p.1
     * <.>, n_structure_angle_bracket_..json // p.1 + p.4
     * abc, n_string_single_string_no_double_quotes.json // p.1 + p.4
     * ["new\n\nline"], n_string_unescaped_newline.json // p.6
     * ```
     */
    private val default = Json

    // Unlike most configuration flag, allowComments flag switches lexer to an entirely different code path,
    // therefore it is tested separately here.
    private val jsonWithComments = Json(default) { allowComments = true }

    @Test
    fun testCompatibility() = forEachSpecTest { testCase, content, shouldPass, json ->
        val jsonResult = runCatching { json.parseToJsonElement(content) }
        if (shouldPass) {
            assertTrue(jsonResult.isSuccess, "Test: $testCase, settings ${json.configuration}, result $jsonResult")
        } else {
            assertTrue(jsonResult.isFailure, "Test: $testCase, settings ${json.configuration}, result $jsonResult")
        }
    }

    @Test
    fun testUnspecifiedParserCases() {
        val resourceListing = SpecConformanceTest::class.java.getResourceAsStream("/corner_cases/listing.txt")
        for (testCase in BufferedReader(InputStreamReader(resourceListing)).lineSequence()) {
            val content = SpecConformanceTest::class.java.getResourceAsStream("/corner_cases/$testCase").readBytes()
                .toString(Charsets.UTF_8).removeSuffix("\n")
            val jsonResult = runCatching { default.parseToJsonElement(content) }
            assertTrue(jsonResult.isSuccess)
        }
    }

    @Serializable
    data class Holder(val s: String)

    @Test
    fun testSerializedFormInClass() = forEachSpecTest { _, content, _, json ->
        assertSerializedAndRestored(Holder(content), Holder.serializer(), json)
        assertSerializedAndRestored(Holder(content.repeat(10)), Holder.serializer(), json)
        assertSerializedAndRestored(Holder(content.repeat(100)), Holder.serializer(), json)
    }

    @Test
    fun testSerializedGoFuzzCorpus() {
        /*
         * Corpus of tests has been taken from the https://github.com/dvyukov/go-fuzz-corpus under Apache 2.0
         */
        ZipInputStream(SpecConformanceTest::class.java.getResourceAsStream("/corpus.zip")).use {
            while (true) {
                it.nextEntry ?: return
                val content = it.readBytes().decodeToString()
                assertSerializedAndRestored(Holder(content), Holder.serializer())
                assertSerializedAndRestored(Holder(content.repeat(10)), Holder.serializer())
                assertSerializedAndRestored(Holder(content.repeat(100)), Holder.serializer())
                it.closeEntry()
            }
        }
    }

    private inline fun forEachSpecTest(block: (testCase: String, content: String, shouldPass: Boolean, json: Json) -> Unit) {
        // Special cases that do not pass normally but pass with allowComments = true
        val commentsListing = SpecConformanceTest::class.java.getResourceAsStream("/spec_cases/listing_comments.txt").reader().readLines()

        val resourceListing = SpecConformanceTest::class.java.getResourceAsStream("/spec_cases/listing.txt")
        for (testCase in BufferedReader(InputStreamReader(resourceListing)).lineSequence()) {
            if (testCase.startsWith("//")) continue // Skip muted tests
            val content = SpecConformanceTest::class.java.getResourceAsStream("/spec_cases/$testCase").readBytes()
                .toString(Charsets.UTF_8)

            val shouldPass = testCase.startsWith("y_")
            block(testCase, content, shouldPass, default)
            block(testCase, content, shouldPass || (testCase in commentsListing), jsonWithComments)
        }
    }
}

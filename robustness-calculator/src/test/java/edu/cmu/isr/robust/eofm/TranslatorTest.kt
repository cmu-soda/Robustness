/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang, David Garlan, Eunsuk Kang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package edu.cmu.isr.robust.eofm

import org.junit.jupiter.api.Test

class TranslatorTest {
  @Test
  fun testCoffeeEOFM() {
    val coffee: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val config = CoffeeConfig()
    val translator = EOFMTranslator(coffee, config.initialValues, config.world, config.relabels)
    val builder = StringBuilder()

    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testCoffeeEOFMError() {
    val coffee: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val config = CoffeeConfig()
    val translator = EOFMTranslator(coffee, config.initialValues, config.world, config.relabels)
    val builder = StringBuilder()

    translator.translate(builder, withError = true)
    println(builder.toString())
  }

  @Test
  fun testTiny() {
    val tiny: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/tiny.xml"))
    val translator = EOFMTranslator(
        tiny,
        mapOf("iX" to "False"),
        listOf(
            "when (iX == False) hA -> hB -> VAR[True]",
            "when (iX == False) hB -> hA -> VAR[True]",
            "when (iX == True) restart -> VAR[False]"
        )
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testOperators() {
    val operators: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/operators.xml"))
    val translator = EOFMTranslator(operators, emptyMap(), emptyList())

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testOmission1() {
    val omissions: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/omission1.xml"))
    val translator = EOFMTranslator(
        omissions,
        mapOf("iX" to "False"),
        listOf(
            "when (iX == False) hA -> hB -> VAR[True]",
            "when (iX == False) hB -> hA -> VAR[True]",
            "when (iX == True) restart -> VAR[False]"
        )
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testBenchmark() {
    val test: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/benchmark.xml"))
    val translator = EOFMTranslator(test, emptyMap(), emptyList())

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testTherac() {
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25.xml"))
    val config = TheracWaitConfig()
    val translator = EOFMTranslator(therac, config.initialValues, config.world, config.relabels)
    val builder = StringBuilder()

    translator.translate(builder)
    println(builder.toString())
  }
}
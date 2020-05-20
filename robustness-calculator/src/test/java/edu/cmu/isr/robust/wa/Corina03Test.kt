/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang
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

package edu.cmu.isr.robust.wa

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class Corina03Test {

  private fun assertWA(wa: String, expected: String) {
    println("Weakest Assumption:")
    println(wa)
    assertEquals(expected, wa)
  }

  @Test
  fun paperExampleTest() {
    val P = "property P = (input -> output -> P)."
    val M1 = "INPUT = (input -> send -> ack -> INPUT)."
    val M2 = "OUTPUT = (send -> SENDING), SENDING = (send -> SENDING | output -> ack -> OUTPUT)."

    val lStar = Corina03(M1, M2, P)
    val wa = lStar.weakestAssumption("WA")
    assertWA(wa, "WA = (send -> WA1 | ack -> WA2),\n" +
        "WA1 = (send -> WA2 | output -> WA3),\n" +
        "WA2 = (send -> WA2 | ack -> WA2 | output -> WA2),\n" +
        "WA3 = (send -> WA2 | ack -> WA) + {send, ack, output}.")
  }
}
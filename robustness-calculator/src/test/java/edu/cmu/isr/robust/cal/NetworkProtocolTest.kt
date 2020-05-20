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

package edu.cmu.isr.robust.cal

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class NetworkProtocolTest {
  @Test
  fun abpTest() {
    val sys = ClassLoader.getSystemResource("specs/abp/abp.lts").readText()
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val p = "property P = (input -> output -> P)."
    val cal = ABPRobustCal(sys, env, p)
    val r = cal.computeRobustness()
    println("Found ${r.size} traces, matched ${r.filter { it.second != null }.size}/${r.size}.")

    // Manually group them again
    val grouped = r.groupBy {
      val explan = it.second
      explan?.filter { a -> cal.isErrEvent(a) }?.joinToString(",") ?: "Unexplained"
    }
    for ((k, v) in grouped) {
      println("Group: $k, Number of traces: ${v.size}")
    }
  }

  @Test
  fun perfectChannelTest() {
    val sys = ClassLoader.getSystemResource("specs/abp/perfect2.lts").readText()
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val p = "property P = (input -> output -> P)."
    val cal = ABPRobustCal(sys, env, p)
    val r = cal.computeRobustness()
    assertEquals(listOf(
        Pair(listOf("send[1]", "rec[0]"), listOf("input", "send[1]", "trans.corrupt", "rec[0]")),
        Pair(listOf("send[1]", "rec[1]", "ack[1]", "getack[0]"), listOf("input", "send[1]", "rec[1]", "output", "ack[1]", "ack.corrupt", "getack[0]")),
        Pair(listOf("send[1]", "rec[1]", "ack[0]", "getack[1]"), listOf("input", "send[1]", "rec[1]", "output", "ack[0]", "ack.corrupt", "getack[1]")),
        Pair(listOf("send[0]", "rec[1]"), listOf("input", "send[0]", "trans.corrupt", "rec[1]"))
    ), r)
    println("Found ${r.size} traces, matched ${r.filter { it.second != null }.size}/${r.size}.")
  }
}
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

package edu.cmu.isr.robust.lts

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.deadlockCheck
import edu.cmu.isr.robust.ltsa.doCompose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LTSTest {
  @Test
  fun testMenu() {
    val errModel = ClassLoader.getSystemResource("specs/abp/abp_env_lossy.lts").readText()
    LTSACall.doCompile(errModel)
    assertEquals(listOf("lose", "duplicate", "corrupt"), LTSACall.menuActions("ERR_ACTS"))
  }

  @Test
  fun testDeadlock() {
    val spec = "A = (a -> b -> c -> A).\n" +
        "B = (a -> c -> b -> B).\n" +
        "||C = (A || B)."
    val composite = LTSACall.doCompile(spec).doCompose()
    assertEquals(listOf("a"), composite.deadlockCheck())
  }

  @Test
  fun testDeadlock2() {
    val spec = "A = (a -> b -> c -> A).\n" +
        "B = (a -> c -> b -> B).\n" +
        "property P = (a -> c -> P).\n" +
        "||C = (A || B || P)."
    val composite = LTSACall.doCompile(spec).doCompose()
    assertEquals(listOf("a"), composite.deadlockCheck())
  }
}
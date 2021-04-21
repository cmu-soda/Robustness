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

package edu.cmu.isr.robust.wa

import edu.cmu.isr.robust.util.StateMachine

class Corina02WithIO(
  sys: String, env: String, p: String,
  private val inputActions: List<String>,
  private val outputActions: List<String>
  ) : Corina02(sys, env, p) {

  override fun weakestAssumption(name: String): String {
    return weakestAssumption(name, true)
  }

  override fun StateMachine.makeSinkState(dfaStates: List<Set<Int>>): StateMachine {
    val alphabetIdx = this.alphabet.indices.filter { it != this.tau && this.alphabet[it] in outputActions }
    return makeSinkState(dfaStates, alphabetIdx)
  }

}
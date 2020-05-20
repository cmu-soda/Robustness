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

package edu.cmu.isr.robust.ltsa

class StringLTSInput(private val source: String) : lts.LTSInput {
  private var pos: Int = -1

  override fun nextChar(): Char {
    ++pos
    return if (pos < source.length) source[pos] else '\u0000'
  }

  override fun backChar(): Char {
    --pos
    return if (pos < 0) {
      pos = 0
      '\u0000'
    } else {
      source[pos]
    }
  }

  override fun getMarker(): Int {
    return pos
  }
}

class StringLTSOutput : lts.LTSOutput {
  private val text: StringBuilder = StringBuilder()

  fun getText(): String {
    return text.toString()
  }

  override fun out(s: String) {
    text.append(s)
  }

  override fun outln(s: String) {
    text.appendln(s)
  }

  override fun clearOutput() {
    text.setLength(0)
  }
}
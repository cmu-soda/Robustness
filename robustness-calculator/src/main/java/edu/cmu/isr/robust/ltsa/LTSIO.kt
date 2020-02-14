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
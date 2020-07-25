package edu.cmu.isr.robust.fuzz.android

class UIIndex {

  /**
   *
   */
  private val statesMap = mutableMapOf<String, Int>()

  fun get(key: String): Int? {
    return statesMap[abstractDump(key)]
  }

  fun put(key: String, value: Int) {
    statesMap[abstractDump(key)] = value
  }

  /**
   *
   */
  fun abstractDump(dump: String): String {
    return dump
        // Remove the text in the text attribute
        .replace("text=\"[\\w|\\s]+\"".toRegex(), "text=\"\"")
        .replace("bounds=\"\\[\\d+,\\d+\\]\\[\\d+,\\d+\\]\"".toRegex(), "bounds=\"\"")
//        .replace("focused=\"(false|true)\"".toRegex(), "focused=\"\"")
  }

}
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
    val wa = lStar.weakestAssumption()
    assertWA(wa, "WA = (send -> WA1 | ack -> WA2),\n" +
        "WA1 = (send -> WA2 | output -> WA3),\n" +
        "WA2 = (send -> WA2 | ack -> WA2 | output -> WA2),\n" +
        "WA3 = (send -> WA2 | ack -> WA) + {send, ack, output}.")
  }
}
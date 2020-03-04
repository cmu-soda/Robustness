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
  }

  @Test
  fun perfectChannelTest() {
    val sys = ClassLoader.getSystemResource("specs/abp/perfect2.lts").readText()
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val p = "property P = (input -> output -> P)."
    val cal = ABPRobustCal(sys, env, p)
    val r = cal.computeRobustness()
    assertEquals(listOf(
        Pair(listOf("send[1]", "rec[0]"), null),
        Pair(listOf("send[1]", "rec[1]", "ack[1]", "getack[0]"), null),
        Pair(listOf("send[1]", "rec[1]", "ack[0]", "getack[1]"), null),
        Pair(listOf("send[0]", "rec[1]"), null)
    ), r)
    println("Found ${r.size} traces, matched ${r.filter { it.second != null }.size}/${r.size}.")
  }
}
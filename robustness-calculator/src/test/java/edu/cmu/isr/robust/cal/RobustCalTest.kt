package edu.cmu.isr.robust.cal

import org.junit.jupiter.api.Test

class RobustCalTest {
  @Test
  fun abpTest() {
    val sys = ClassLoader.getSystemResource("specs/abp/abp.lts").readText()
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val p = "property P = (input -> output -> P)."
    val cal = ABPRobustCal(sys, env, p)
    val r = cal.errsRobustAgainst()
    println("Found ${r.size} traces, matched ${r.filter { it.second != null }.size}/${r.size}.")
  }

  @Test
  fun perfectChannelTest() {
    val sys = ClassLoader.getSystemResource("specs/abp/perfect2.lts").readText()
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val p = "property P = (input -> output -> P)."
    val cal = ABPRobustCal(sys, env, p)
    cal.errsRobustAgainst()
    val r = cal.errsRobustAgainst()
    println("Found ${r.size} traces, matched ${r.filter { it.second != null }.size}/${r.size}.")
  }
}
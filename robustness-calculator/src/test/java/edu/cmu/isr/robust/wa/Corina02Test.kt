package edu.cmu.isr.robust.wa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Corina02Test {

  private fun assertWA(wa: String, expected: String) {
    println("Weakest Assumption:")
    println(wa)
    assertEquals(expected, wa)
  }

  private fun assertTraces(traces: Map<AbstractWAGenerator.EquivClass, List<List<String>>>, t: String, expected: List<String>) {
    println("$t delta Traces:")
    for ((k, v) in traces) {
      println("Class $k:")
      for (l in v)
        println("\t$l")
    }
    println()
    assertEquals(expected, traces.values.flatMap { v -> v.map { "[${it.joinToString(", ")}]" } })
  }

  @Test
  fun testPerfectChannel() {
    val p = "property P = (input -> output -> P)."
    val env = ClassLoader.getSystemResource("specs/abp/env.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/abp/perfect.lts").readText()

    val cal = Corina02(sys, env, p)
    val wa = cal.weakestAssumption("WA")
    assertWA(wa, "WA = (send -> WA_1),\n" +
        "WA_1 = (rec -> WA_2),\n" +
        "WA_2 = (ack -> WA_3),\n" +
        "WA_3 = (getack -> WA).\n")
    assertTraces(cal.shortestDeltaTraces(wa, "WA"), "Shortest", emptyList())
  }

  @Test
  fun testPerfectChannel2() {
    val p = "property P = (input -> output -> P)."
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/abp/perfect2.lts").readText()

    val cal = Corina02(sys, env, p)
    val wa = cal.weakestAssumption("WA")
    assertWA(wa, "WA = (send[0] -> WA_1 | send[1] -> WA_1),\n" +
        "WA_1 = (rec[0] -> WA_2 | rec[1] -> WA_2),\n" +
        "WA_2 = (ack[0] -> WA_3 | ack[1] -> WA_3),\n" +
        "WA_3 = (getack[0] -> WA | getack[1] -> WA).\n")
    assertTraces(cal.shortestDeltaTraces(wa, "WA"), "Shortest", listOf(
        "[send[1], rec[0]]",
        "[send[1], rec[1], ack[1], getack[0]]",
        "[send[1], rec[1], ack[0], getack[1]]",
        "[send[0], rec[1]]"
    ))
    assertTraces(cal.deltaTraces(wa, "WA", level = 0), "Level 0", listOf(
        "[send[0], rec[0], ack[0], getack[1]]",
        "[send[1], rec[1], ack[0], getack[1]]",
        "[send[0], rec[0], ack[1], getack[0]]",
        "[send[1], rec[1], ack[1], getack[0]]",
        "[send[0], rec[1]]",
        "[send[1], rec[0]]"
    ))
    assertTraces(cal.deltaTraces(wa, "WA", level = 1), "Level 1", listOf(
        "[send[0], rec[0], ack[0], getack[0], send[0], rec[0], ack[0], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[0], getack[1]]",
        "[send[0], rec[0], ack[0], getack[1]]",
        "[send[0], rec[0], ack[1], getack[1], send[0], rec[0], ack[0], getack[1]]",
        "[send[0], rec[0], ack[1], getack[1], send[1], rec[1], ack[0], getack[1]]",
        "[send[1], rec[1], ack[0], getack[0], send[0], rec[0], ack[0], getack[1]]",
        "[send[1], rec[1], ack[0], getack[0], send[1], rec[1], ack[0], getack[1]]",
        "[send[1], rec[1], ack[0], getack[1]]",
        "[send[1], rec[1], ack[1], getack[1], send[0], rec[0], ack[0], getack[1]]",
        "[send[1], rec[1], ack[1], getack[1], send[1], rec[1], ack[0], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[0], rec[0], ack[1], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[0]]",
        "[send[0], rec[0], ack[1], getack[0]]",
        "[send[0], rec[0], ack[1], getack[1], send[0], rec[0], ack[1], getack[0]]",
        "[send[0], rec[0], ack[1], getack[1], send[1], rec[1], ack[1], getack[0]]",
        "[send[1], rec[1], ack[0], getack[0], send[0], rec[0], ack[1], getack[0]]",
        "[send[1], rec[1], ack[0], getack[0], send[1], rec[1], ack[1], getack[0]]",
        "[send[1], rec[1], ack[1], getack[0]]",
        "[send[1], rec[1], ack[1], getack[1], send[0], rec[0], ack[1], getack[0]]",
        "[send[1], rec[1], ack[1], getack[1], send[1], rec[1], ack[1], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[0], rec[1]]",
        "[send[0], rec[0], ack[1], getack[1], send[0], rec[1]]",
        "[send[0], rec[1]]",
        "[send[1], rec[1], ack[0], getack[0], send[0], rec[1]]",
        "[send[1], rec[1], ack[1], getack[1], send[0], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[0]]",
        "[send[0], rec[0], ack[1], getack[1], send[1], rec[0]]",
        "[send[1], rec[0]]",
        "[send[1], rec[1], ack[0], getack[0], send[1], rec[0]]",
        "[send[1], rec[1], ack[1], getack[1], send[1], rec[0]]"
    ))
  }

  @Test
  fun testABP() {
    val p = "property P = (input -> output -> P)."
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/abp/abp.lts").readText()

    val cal = Corina02(sys, env, p)
    val wa = cal.weakestAssumption("WA")
    assertWA(wa, "WA = (send[0] -> WA_1 | getack[1] -> WA_1),\n" +
        "WA_1 = (send[0] -> WA_1 | getack[1] -> WA_1 | rec[0] -> WA_2),\n" +
        "WA_2 = (send[0] -> WA_2 | getack[1] -> WA_2 | rec[0] -> WA_3 | ack[0] -> WA_3),\n" +
        "WA_3 = (send[0] -> WA_3 | getack[1] -> WA_3 | rec[0] -> WA_3 | ack[0] -> WA_3 | getack[0] -> WA_4),\n" +
        "WA_4 = (rec[0] -> WA_4 | ack[0] -> WA_4 | getack[0] -> WA_5 | send[1] -> WA_5),\n" +
        "WA_5 = (rec[0] -> WA_5 | ack[0] -> WA_5 | getack[0] -> WA_5 | send[1] -> WA_5 | rec[1] -> WA_6),\n" +
        "WA_6 = (getack[0] -> WA_6 | send[1] -> WA_6 | rec[1] -> WA_7 | ack[1] -> WA_7),\n" +
        "WA_7 = (getack[1] -> WA_8 | getack[0] -> WA_7 | send[1] -> WA_7 | rec[1] -> WA_7 | ack[1] -> WA_7),\n" +
        "WA_8 = (send[0] -> WA_9 | getack[1] -> WA_9 | rec[1] -> WA_8 | ack[1] -> WA_8),\n" +
        "WA_9 = (send[0] -> WA_9 | getack[1] -> WA_9 | rec[0] -> WA_2 | rec[1] -> WA_9 | ack[1] -> WA_9).\n")
    assertTraces(cal.shortestDeltaTraces(wa, "WA"), "Shortest", listOf(
        "[getack[1]]",
        "[send[0], send[0]]",
        "[send[0], getack[1]]",
        "[send[0], rec[0], rec[0]]",
        "[send[0], rec[0], getack[1]]",
        "[send[0], rec[0], ack[0], ack[0]]",
        "[send[0], rec[0], ack[0], rec[0]]",
        "[send[0], rec[0], ack[0], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], ack[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], getack[0], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], getack[0], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], getack[0], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], getack[0], ack[0], ack[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], getack[0], ack[0], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], getack[0], ack[0], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], send[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], ack[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], getack[1], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], getack[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], getack[1], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], getack[1], ack[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], getack[1], ack[1], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], getack[1], ack[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], send[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], ack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], send[0], send[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], send[0], ack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], send[0], rec[0], ack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], send[0], rec[0], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], send[0], rec[0], getack[0]]",
        "[send[0], rec[0], send[0], rec[0], rec[0]]",
        "[send[0], rec[0], send[0], rec[0], getack[0]]",
        "[send[0], rec[0], send[0], rec[0], getack[1]]",
        "[send[0], rec[0], send[0], rec[0], send[0], send[0]]",
        "[send[0], rec[0], send[0], rec[0], send[0], getack[0]]",
        "[send[0], rec[0], send[0], rec[0], send[0], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], send[0], rec[0], send[0], send[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], send[0], rec[0], send[0], ack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], ack[1], send[0], rec[0], send[0], rec[0], send[0], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], send[0], send[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], send[0], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], getack[1], send[0], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], send[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], send[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], ack[1], send[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], ack[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], ack[1], getack[1], send[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], ack[1], getack[1], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], ack[1], getack[1], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], ack[1], getack[1], ack[1], send[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], ack[1], getack[1], ack[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], ack[1], send[1], getack[1], ack[1], getack[1], ack[1], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], send[1], send[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], send[1], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], send[1], rec[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], send[1], rec[1], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], send[1], rec[1], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], send[1], rec[1], send[1], send[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], send[1], rec[1], send[1], getack[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[1], send[1], rec[1], send[1], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], send[1], send[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], send[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], send[1], rec[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], send[1], rec[1], rec[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], send[1], rec[1], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], send[1], rec[1], send[1], send[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], send[1], rec[1], send[1], ack[1]]",
        "[send[0], rec[0], ack[0], getack[0], ack[0], send[1], rec[1], send[1], rec[1], send[1], getack[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], send[1]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], rec[0]]",
        "[send[0], rec[0], ack[0], getack[0], send[1], getack[0]]",
        "[send[0], rec[0], ack[0], send[0], send[0]]",
        "[send[0], rec[0], ack[0], send[0], ack[0]]",
        "[send[0], rec[0], ack[0], send[0], getack[1]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], send[1]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], getack[0]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], ack[0], send[1]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], ack[0], ack[0]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], ack[0], getack[0], send[1]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], ack[0], getack[0], rec[1]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], ack[0], getack[0], getack[0]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], ack[0], getack[0], ack[0], send[1]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], ack[0], getack[0], ack[0], ack[0]]",
        "[send[0], rec[0], ack[0], send[0], getack[0], ack[0], getack[0], ack[0], rec[1]]",
        "[send[0], rec[0], send[0], send[0]]",
        "[send[0], rec[0], send[0], getack[1]]"
    ))
  }

  @Test
  fun testCoffee() {
    val p = ClassLoader.getSystemResource("specs/coffee/coffee_p.lts").readText()
    val env = ClassLoader.getSystemResource("specs/coffee/coffee_human.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee/coffee.lts").readText()

    val cal = Corina02(sys, env, p)
    val wa = cal.weakestAssumption("WA")
//    assertWA(wa, "WA = (hPressBrew -> WA | hLowerHandle -> WA_6 | hPlaceMug -> WA_1),\n" +
//        "WA_1 = (hPressBrew -> WA_1 | hLowerHandle -> WA_2 | hTakeMug -> WA),\n" +
//        "WA_2 = (hPressBrew -> WA_3 | hLiftHandle -> WA_1 | hTakeMug -> WA_6),\n" +
//        "WA_3 = (complete -> WA_4),\n" +
//        "WA_4 = (hLiftHandle -> WA_5 | hTakeMug -> WA_6),\n" +
//        "WA_5 = (hPressBrew -> WA_5 | hLowerHandle -> WA_4 | hTakeMug -> WA),\n" +
//        "WA_6 = (hPlaceMug -> WA_2 | hLiftHandle -> WA).\n")
    assertWA(wa, "WA = (hPressBrew -> WA | hLowerHandle -> WA_1),\n" +
        "WA_1 = (hLiftHandle -> WA)+{complete}.\n")
  }

  @Test
  fun testCoffeeR() {
    val p = ClassLoader.getSystemResource("specs/coffee/coffee_p.lts").readText()
    val env = ClassLoader.getSystemResource("specs/coffee/coffee_human.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee/coffee_r.lts").readText()

    val cal = Corina02(sys, env, p)
    val wa = cal.weakestAssumption("WA")
//    assertWA(wa, "WA = (hPressBrew -> WA | hPlaceMug -> WA_6 | hLowerHandle -> WA_1),\n" +
//        "WA_1 = (hPressBrew -> WA_1 | hPlaceMug -> WA_2 | hLiftHandle -> WA),\n" +
//        "WA_2 = (hPressBrew -> WA_3 | hTakeMug -> WA_1 | hLiftHandle -> WA_6),\n" +
//        "WA_3 = (complete -> WA_4),\n" +
//        "WA_4 = (hTakeMug -> WA_1 | hLiftHandle -> WA_5),\n" +
//        "WA_5 = (hPressBrew -> WA_5 | hLowerHandle -> WA_4 | hTakeMug -> WA),\n" +
//        "WA_6 = (hPressBrew -> WA_6 | hLowerHandle -> WA_2 | hTakeMug -> WA).\n")
    assertWA(wa, "WA = (hPressBrew -> WA | hPlaceMug -> WA_4 | hLowerHandle -> WA_1),\n" +
        "WA_1 = (hPressBrew -> WA_1 | hPlaceMug -> WA_2 | hLiftHandle -> WA),\n" +
        "WA_2 = (hPressBrew -> WA_3 | hTakeMug -> WA_1 | hLiftHandle -> WA_4),\n" +
        "WA_3 = (complete -> WA_2),\n" +
        "WA_4 = (hPressBrew -> WA_4 | hLowerHandle -> WA_2 | hTakeMug -> WA).\n")
  }

  @Test
  fun testCoffeeEOFM() {
    val p = ClassLoader.getSystemResource("specs/coffee_eofm/p.lts").readText()
    val env = ClassLoader.getSystemResource("specs/coffee_eofm/human.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee_eofm/machine.lts").readText()

    val cal = Corina02(sys, env, p)
    val wa = cal.weakestAssumption("COFFEE")
//    assertWA(wa, "COFFEE = (hLiftHandle -> COFFEE_4 | hPlaceMug -> COFFEE_1),\n" +
//        "COFFEE_1 = (hLiftHandle -> COFFEE_3 | hPressBrew -> COFFEE_2 | hTakeMug -> COFFEE),\n" +
//        "COFFEE_2 = (mBrewDone -> COFFEE_1),\n" +
//        "COFFEE_3 = (hLowerHandle -> COFFEE_1 | hPressBrew -> COFFEE_3 | hTakeMug -> COFFEE_4),\n" +
//        "COFFEE_4 = (hPlaceMug -> COFFEE_3 | hLowerHandle -> COFFEE | hPressBrew -> COFFEE_4).\n")
    assertWA(wa, "COFFEE = (hLiftHandle -> COFFEE_1),\n" +
        "COFFEE_1 = (hLowerHandle -> COFFEE | hPressBrew -> COFFEE_1)+{mBrewDone}.\n")

    assertTraces(cal.shortestDeltaTraces(wa, "COFFEE"), "Shortest", listOf(
        "[hLiftHandle, hPressBrew]",
        "[hLiftHandle, hLowerHandle, hLiftHandle, hPressBrew]"
//        "[hPlaceMug, hPressBrew]",
//        "[hPlaceMug, hTakeMug]",
//        "[hPlaceMug, hLiftHandle, hPressBrew]",
//        "[hPlaceMug, hLiftHandle, hTakeMug]",
//        "[hLiftHandle, hLowerHandle, hPlaceMug, hTakeMug]",
//        "[hLiftHandle, hLowerHandle, hPlaceMug, hPressBrew, mBrewDone, hLiftHandle]",
//        "[hLiftHandle, hLowerHandle, hPlaceMug, hPressBrew, mBrewDone, hPressBrew]",
//        "[hLiftHandle, hLowerHandle, hLiftHandle, hPlaceMug, hPressBrew]",
//        "[hLiftHandle, hLowerHandle, hLiftHandle, hPlaceMug, hTakeMug]",
//        "[hLiftHandle, hPressBrew]",
//        "[hLiftHandle, hLowerHandle, hLiftHandle, hPressBrew]"
    ))
  }
}
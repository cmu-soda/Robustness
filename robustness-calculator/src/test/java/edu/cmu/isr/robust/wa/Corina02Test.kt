package edu.cmu.isr.robust.wa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Corina02Test {
  @Test
  fun testPerfectChannel() {
    val p = "property P = (input -> output -> P)."
    val env = ClassLoader.getSystemResource("specs/abp/env.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/abp/perfect.lts").readText()

    val cal = Corina02(p, env, sys)
    val wa = cal.weakestAssumption()
    println(wa)
    assertEquals("WA = (send -> WA_1),\n" +
        "WA_1 = (rec -> WA_2),\n" +
        "WA_2 = (ack -> WA_3),\n" +
        "WA_3 = (getack -> WA).\n", wa)
    val traces = cal.shortestErrTraces(wa)
    println(traces)
    assertEquals(emptyList<List<String>>(), traces)
  }

  @Test
  fun testPerfectChannel2() {
    val p = "property P = (input -> output -> P)."
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/abp/perfect2.lts").readText()

    val cal = Corina02(p, env, sys)
    val wa = cal.weakestAssumption()
    println(wa)
    assertEquals("WA = (send[0] -> WA_1 | send[1] -> WA_1),\n" +
        "WA_1 = (rec[0] -> WA_2 | rec[1] -> WA_2),\n" +
        "WA_2 = (ack[0] -> WA_3 | ack[1] -> WA_3),\n" +
        "WA_3 = (getack[0] -> WA | getack[1] -> WA).\n", wa)
    val traces = cal.shortestErrTraces(wa)
    println(traces)
    assertEquals(listOf(
        listOf("send[1]", "rec[0]"),
        listOf("send[1]", "rec[1]", "ack[1]", "getack[0]"),
        listOf("send[1]", "rec[1]", "ack[0]", "getack[1]"),
        listOf("send[0]", "rec[1]")
    ), traces)
  }

  @Test
  fun testABP() {
    val p = "property P = (input -> output -> P)."
    val env = ClassLoader.getSystemResource("specs/abp/abp_env.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/abp/abp.lts").readText()

    val cal = Corina02(p, env, sys)
    val wa = cal.weakestAssumption()
    println(wa)
    assertEquals("WA = (send[0] -> WA_1 | getack[1] -> WA_1),\n" +
        "WA_1 = (send[0] -> WA_1 | getack[1] -> WA_1 | rec[0] -> WA_2),\n" +
        "WA_2 = (send[0] -> WA_2 | getack[1] -> WA_2 | rec[0] -> WA_3 | ack[0] -> WA_3),\n" +
        "WA_3 = (send[0] -> WA_3 | getack[1] -> WA_3 | rec[0] -> WA_3 | ack[0] -> WA_3 | getack[0] -> WA_4),\n" +
        "WA_4 = (rec[0] -> WA_4 | ack[0] -> WA_4 | getack[0] -> WA_5 | send[1] -> WA_5),\n" +
        "WA_5 = (rec[0] -> WA_5 | ack[0] -> WA_5 | getack[0] -> WA_5 | send[1] -> WA_5 | rec[1] -> WA_6),\n" +
        "WA_6 = (getack[0] -> WA_6 | send[1] -> WA_6 | rec[1] -> WA_7 | ack[1] -> WA_7),\n" +
        "WA_7 = (getack[1] -> WA_8 | getack[0] -> WA_7 | send[1] -> WA_7 | rec[1] -> WA_7 | ack[1] -> WA_7),\n" +
        "WA_8 = (send[0] -> WA_9 | getack[1] -> WA_9 | rec[1] -> WA_8 | ack[1] -> WA_8),\n" +
        "WA_9 = (send[0] -> WA_9 | getack[1] -> WA_9 | rec[0] -> WA_2 | rec[1] -> WA_9 | ack[1] -> WA_9).\n", wa)
    val traces = cal.shortestErrTraces(wa)
    println(traces)
  }

  @Test
  fun testCoffee() {
    val p = ClassLoader.getSystemResource("specs/coffee/coffee_p.lts").readText()
    val env = ClassLoader.getSystemResource("specs/coffee/coffee_human.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee/coffee.lts").readText()

    val cal = Corina02(p, env, sys)
    val spec = cal.weakestAssumption()
    println(spec)
    assertEquals("WA = (hPressBrew -> WA | hLowerHandle -> WA_6 | hPlaceMug -> WA_1),\n" +
        "WA_1 = (hPressBrew -> WA_1 | hLowerHandle -> WA_2 | hTakeMug -> WA),\n" +
        "WA_2 = (hPressBrew -> WA_3 | hLiftHandle -> WA_1 | hTakeMug -> WA_6),\n" +
        "WA_3 = (complete -> WA_4),\n" +
        "WA_4 = (hLiftHandle -> WA_5 | hTakeMug -> WA_6),\n" +
        "WA_5 = (hPressBrew -> WA_5 | hLowerHandle -> WA_4 | hTakeMug -> WA),\n" +
        "WA_6 = (hPlaceMug -> WA_2 | hLiftHandle -> WA).\n", spec)
  }

  @Test
  fun testCoffeeR() {
    val p = ClassLoader.getSystemResource("specs/coffee/coffee_p.lts").readText()
    val env = ClassLoader.getSystemResource("specs/coffee/coffee_human.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee/coffee_r.lts").readText()

    val cal = Corina02(p, env, sys)
    val spec = cal.weakestAssumption()
    println(spec)
    assertEquals("WA = (hPressBrew -> WA | hPlaceMug -> WA_6 | hLowerHandle -> WA_1),\n" +
        "WA_1 = (hPressBrew -> WA_1 | hPlaceMug -> WA_2 | hLiftHandle -> WA),\n" +
        "WA_2 = (hPressBrew -> WA_3 | hTakeMug -> WA_1 | hLiftHandle -> WA_6),\n" +
        "WA_3 = (complete -> WA_4),\n" +
        "WA_4 = (hTakeMug -> WA_1 | hLiftHandle -> WA_5),\n" +
        "WA_5 = (hPressBrew -> WA_5 | hLowerHandle -> WA_4 | hTakeMug -> WA),\n" +
        "WA_6 = (hPressBrew -> WA_6 | hLowerHandle -> WA_2 | hTakeMug -> WA).\n", spec)
  }

  @Test
  fun testCoffeeEOFM() {
    val p = ClassLoader.getSystemResource("specs/coffee_eofm/p.lts").readText()
    val env = ClassLoader.getSystemResource("specs/coffee_eofm/human.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee_eofm/machine.lts").readText()

    val cal = Corina02(p, env, sys)
    val wa = cal.weakestAssumption("COFFEE")
    println(wa)
    assertEquals("COFFEE = (hLiftHandle -> COFFEE_4 | hPlaceMug -> COFFEE_1),\n" +
        "COFFEE_1 = (hLiftHandle -> COFFEE_3 | hPressBrew -> COFFEE_2 | hTakeMug -> COFFEE),\n" +
        "COFFEE_2 = (mBrewDone -> COFFEE_1),\n" +
        "COFFEE_3 = (hLowerHandle -> COFFEE_1 | hPressBrew -> COFFEE_3 | hTakeMug -> COFFEE_4),\n" +
        "COFFEE_4 = (hPlaceMug -> COFFEE_3 | hLowerHandle -> COFFEE | hPressBrew -> COFFEE_4).\n", wa)

    val traces = cal.shortestErrTraces(wa, "COFFEE")
    for (t in traces)
      println(t)
    assertEquals(listOf(
        listOf("hPlaceMug", "hPressBrew"),
        listOf("hPlaceMug", "hTakeMug"),
        listOf("hPlaceMug", "hLiftHandle", "hPressBrew"),
        listOf("hPlaceMug", "hLiftHandle", "hTakeMug"),
        listOf("hLiftHandle", "hLowerHandle", "hPlaceMug", "hTakeMug"),
        listOf("hLiftHandle", "hLowerHandle", "hPlaceMug", "hPressBrew", "mBrewDone", "hLiftHandle"),
        listOf("hLiftHandle", "hLowerHandle", "hPlaceMug", "hPressBrew", "mBrewDone", "hPressBrew"),
        listOf("hLiftHandle", "hLowerHandle", "hLiftHandle", "hPlaceMug", "hPressBrew"),
        listOf("hLiftHandle", "hLowerHandle", "hLiftHandle", "hPlaceMug", "hTakeMug"),
        listOf("hLiftHandle", "hPressBrew"),
        listOf("hLiftHandle", "hLowerHandle", "hLiftHandle", "hPressBrew")
    ), traces)
  }
}
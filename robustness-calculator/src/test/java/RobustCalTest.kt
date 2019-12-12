import edu.cmu.isr.ltsa.weakest.RobustCal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RobustCalTest {
  @Test
  fun testPerfectChannel() {
    val p = "property P = (input -> output -> P)."
    val env = ClassLoader.getSystemResource("specs/env.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/perfect.lts").readText()

    val cal = RobustCal(p, env, sys)
    val spec = cal.deltaEnv("WE")
    println(spec)
    assertEquals("property ENV = (send -> rec -> ack -> getack -> ENV).\n" +
        "WE = (send -> WE_1),\n" +
        "WE_1 = (rec -> WE_2),\n" +
        "WE_2 = (ack -> WE_3),\n" +
        "WE_3 = (getack -> WE).\n" +
        "||D_WE = (ENV || WE)@{send, getack, rec, ack}.", spec)
  }

  @Test
  fun testABP() {
    val p = "property P = (input -> output -> P)."
    val env = ClassLoader.getSystemResource("specs/env2.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/abp.lts").readText()

    val cal = RobustCal(p, env, sys)
    val spec = cal.deltaEnv("WE")
    println(spec)
    assertEquals("property ENV = (send[0..1] -> rec[0..1] -> ack[0..1] -> getack[0..1] -> ENV).\n" +
        "WE = (send[0] -> WE_9 | ack[1] -> WE_1),\n" +
        "WE_1 = (send[0] -> WE_2 | ack[1] -> WE_1 | rec[1] -> WE_1),\n" +
        "WE_2 = (send[0] -> WE_2 | ack[1] -> WE_2 | getack[1] -> WE_2 | rec[1] -> WE_2 | rec[0] -> WE_3),\n" +
        "WE_3 = (send[0] -> WE_3 | getack[1] -> WE_3 | ack[0] -> WE_4),\n" +
        "WE_4 = (send[0] -> WE_4 | getack[1] -> WE_4 | rec[0] -> WE_4 | ack[0] -> WE_4 | getack[0] -> WE_5),\n" +
        "WE_5 = (rec[0] -> WE_5 | ack[0] -> WE_5 | send[1] -> WE_6),\n" +
        "WE_6 = (rec[1] -> WE_7 | rec[0] -> WE_6 | ack[0] -> WE_6 | getack[0] -> WE_6 | send[1] -> WE_6),\n" +
        "WE_7 = (ack[1] -> WE_8 | getack[0] -> WE_7 | send[1] -> WE_7),\n" +
        "WE_8 = (ack[1] -> WE_8 | getack[1] -> WE_1 | rec[1] -> WE_8 | getack[0] -> WE_8 | send[1] -> WE_8),\n" +
        "WE_9 = (send[0] -> WE_9 | ack[1] -> WE_2 | getack[1] -> WE_9).\n" +
        "||D_WE = (ENV || WE)@{send, getack, ack, rec}.", spec)
  }

  @Test
  fun testCoffee() {
    val p = ClassLoader.getSystemResource("specs/coffee_p.lts").readText()
    val env = ClassLoader.getSystemResource("specs/coffee_human.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee.lts").readText()

    val cal = RobustCal(p, env, sys)
    val spec = cal.deltaEnv("WE")
    println(spec)
    assertEquals("property ENV = (hPlaceMug -> hAddOrReplacePod -> hLowerHandle -> hPressBrew -> complete -> hLiftHandle -> hTakeMug -> ENV).\n" +
        "WE = (hPressBrew -> WE | hLowerHandle -> WE_6 | hPlaceMug -> WE_1),\n" +
        "WE_1 = (hPressBrew -> WE_1 | hLowerHandle -> WE_2 | hTakeMug -> WE),\n" +
        "WE_2 = (hPressBrew -> WE_3 | hLiftHandle -> WE_1 | hTakeMug -> WE_6),\n" +
        "WE_3 = (complete -> WE_4),\n" +
        "WE_4 = (hLiftHandle -> WE_5 | hTakeMug -> WE_6),\n" +
        "WE_5 = (hPressBrew -> WE_5 | hLowerHandle -> WE_4 | hTakeMug -> WE),\n" +
        "WE_6 = (hPlaceMug -> WE_2 | hLiftHandle -> WE).\n" +
        "||D_WE = (ENV || WE)@{hLiftHandle, hPressBrew, hLowerHandle, complete, hPlaceMug, hTakeMug}.", spec)
  }

  @Test
  fun testCoffeeR() {
    val p = ClassLoader.getSystemResource("specs/coffee_p.lts").readText()
    val env = ClassLoader.getSystemResource("specs/coffee_human.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee_r.lts").readText()

    val cal = RobustCal(p, env, sys)
    val spec = cal.deltaEnv("WE")
    println(spec)
    assertEquals("property ENV = (hPlaceMug -> hAddOrReplacePod -> hLowerHandle -> hPressBrew -> complete -> hLiftHandle -> hTakeMug -> ENV).\n" +
        "WE = (hPressBrew -> WE | hPlaceMug -> WE_6 | hLowerHandle -> WE_1),\n" +
        "WE_1 = (hPressBrew -> WE_1 | hPlaceMug -> WE_2 | hLiftHandle -> WE),\n" +
        "WE_2 = (hPressBrew -> WE_3 | hTakeMug -> WE_1 | hLiftHandle -> WE_6),\n" +
        "WE_3 = (complete -> WE_4),\n" +
        "WE_4 = (hTakeMug -> WE_1 | hLiftHandle -> WE_5),\n" +
        "WE_5 = (hPressBrew -> WE_5 | hLowerHandle -> WE_4 | hTakeMug -> WE),\n" +
        "WE_6 = (hPressBrew -> WE_6 | hLowerHandle -> WE_2 | hTakeMug -> WE).\n" +
        "||D_WE = (ENV || WE)@{hLiftHandle, hPressBrew, hPlaceMug, hTakeMug, hLowerHandle, complete}.", spec)
  }
}
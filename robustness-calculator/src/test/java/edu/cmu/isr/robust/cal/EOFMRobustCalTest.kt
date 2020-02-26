package edu.cmu.isr.robust.cal

import edu.cmu.isr.robust.eofm.*
import org.junit.jupiter.api.Test

class EOFMRobustCalTest {
  @Test
  fun testCoffee() {
    val p = ClassLoader.getSystemResource("specs/coffee_eofm/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee_eofm/machine.lts").readText()
    val coffee: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val config = CoffeeConfig()
    val cal = EOFMRobustCal.create(sys, p, coffee, config.initialValues, config.world, config.relabels)

    cal.computeRobustness()
  }

  /**
   * therac25.xml model defines wait power level actions.
   */
  /*@Test
  fun testTheracWait() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25.xml"))
    val config = TheracWaitConfig()
    val cal = EOFMRobustCal.create(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.errsRobustAgainst()
  }*/

  /**
   * In the therac25_nowait.xml model, although the EOFM defines a wait action, but this action is the internal action
   * of human and unobservable from the machine.
   */
  @Test
  fun testTheracNoWait() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_nowait.xml"))
    val config = TheracNoWaitConfig()
    val cal = EOFMRobustCal.create(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.computeRobustness()
  }

  @Test
  fun testTheracRobustNoWait() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys_r.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_nowait.xml"))
    val config = TheracNoWaitConfig()
    val cal = EOFMRobustCal.create(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.computeRobustness()
  }

  @Test
  fun testCompareTheracAndTheracRobust() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_nowait.xml"))
    val config = TheracNoWaitConfig()
    // Read therac
    val sys1 = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val cal1 = EOFMRobustCal.create(sys1, p, therac, config.initialValues, config.world, config.relabels)
    cal1.nameOfWA = "WA1"

    // Read therac robust
    val sys2 = ClassLoader.getSystemResource("specs/therac25/sys_r.lts").readText()
    val cal2 = EOFMRobustCal.create(sys2, p, therac, config.initialValues, config.world, config.relabels)
    cal2.robustnessComparedTo(cal1.getWA(), "WA1", level = 0)
  }

}
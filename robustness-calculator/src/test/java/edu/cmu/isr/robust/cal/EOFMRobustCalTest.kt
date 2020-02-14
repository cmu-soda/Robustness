package edu.cmu.isr.robust.cal

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.util.StateMachine
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

    cal.errsRobustAgainst()
  }

//  @Test
//  fun testCoffee2() {
//    val p = ClassLoader.getSystemResource("specs/coffee_eofm/p.lts").readText()
//    val sys = ClassLoader.getSystemResource("specs/coffee_eofm/machine.lts").readText()
//    val coffee: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
//    val config = CoffeeConfig()
//    val cal = EOFMRobustCal.create(sys, p, coffee, config.initialValues, config.world, config.relabels)
//
//    cal.errsNotRobustAgainst("omission_APrepMachine")
//    cal.errsNotRobustAgainst("omission_APlaceMug", "omission_APrepMachine")
//    cal.errsNotRobustAgainst("omission_AWait")
//  }

  @Test
  fun testTherac() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25.xml"))
    val config = TheracConfig()
    val cal = EOFMRobustCal.create(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.errsRobustAgainst()
  }

//  @Test
//  fun testTherac2() {
//    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
//    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
//    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25.xml"))
//    val config = TheracConfig()
//    val cal = EOFMRobustCal(sys, p, therac, config.initialValues, config.world, config.relabels)
//
//    cal.errsNotRobustAgainst("omission_AWaitReady")
//    cal.errsNotRobustAgainst("omission_AWaitInPlace", "omission_AWaitReady")
//  }

  @Test
  fun testTherac2() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_2.xml"))
    val config = TheracConfig2()
    val cal = EOFMRobustCal.create(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.errsRobustAgainst()
  }

  @Test
  fun testTheracR() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys_r.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25.xml"))
    val config = TheracConfig()
    val cal = EOFMRobustCal.create(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.errsRobustAgainst()
  }

}
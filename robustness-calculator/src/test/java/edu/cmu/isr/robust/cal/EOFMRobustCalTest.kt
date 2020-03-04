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

  @Test
  fun testCoffeeRobust() {
    val p = ClassLoader.getSystemResource("specs/coffee_eofm/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee_eofm/machine_r.lts").readText()
    val coffee: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val config = CoffeeConfig()
    val cal = EOFMRobustCal.create(sys, p, coffee, config.initialValues, config.world, config.relabels)

    cal.computeRobustness()
  }

}
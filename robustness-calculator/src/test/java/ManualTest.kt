import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.parseEOFMS
import edu.cmu.isr.ltsa.util.StateMachine
import edu.cmu.isr.ltsa.weakest.EOFMRobustCal
import org.junit.jupiter.api.Test

class ManualTest {
  @Test
  fun genCoffeeHumanTest() {
    val s = ClassLoader.getSystemResource("specs/coffee_eofm/generate_human.lts").readText()
    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(s, "G").doCompose()
    val sm = StateMachine(composite.composition)
    val dfa = sm.tauElmAndSubsetConstr().first
    print(dfa.minimize().buildFSP("ENV"))
  }

  @Test
  fun genCoffeeHumanErrTest() {
    val s = ClassLoader.getSystemResource("specs/coffee_eofm/generate_human_error.lts").readText()
    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(s, "G").doCompose()
    val sm = StateMachine(composite.composition)
    val dfa = sm.tauElmAndSubsetConstr().first
    print(dfa.minimize().buildFSP("ENV"))
  }

  @Test
  fun testErrorTrace1() {
    val s = ClassLoader.getSystemResource("specs/coffee_eofm/test_error_trace.lts").readText()
    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(s, "T").doCompose()
    val sm = StateMachine(composite.composition)

    val paths = sm.pathToInit()
//    val traces = mutableListOf<String>()
    val transToErr = sm.transitions.inTrans()[-1] ?: emptyList()
    for (t in transToErr) {
      val trace = (paths[t.first] ?: error(t.first)) + sm.alphabet[t.second]
      println("Length: ${trace.size}")
      println("TRACE = (\n${trace.joinToString(" -> \n")} -> END).\n")
    }
  }

  @Test
  fun testCoffee() {
    val p = ClassLoader.getSystemResource("specs/coffee_eofm/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee_eofm/machine.lts").readText()
    val coffee: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val config = CoffeeConfig()
    val cal = EOFMRobustCal(sys, p, coffee, config.initialValues, config.world, config.relabels)

    cal.errsRobustAgainst()
  }

  @Test
  fun testCoffee2() {
    val p = ClassLoader.getSystemResource("specs/coffee_eofm/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee_eofm/machine.lts").readText()
    val coffee: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val config = CoffeeConfig()
    val cal = EOFMRobustCal(sys, p, coffee, config.initialValues, config.world, config.relabels)

    cal.errsNotRobustAgainst("omission_APrepMachine")
    cal.errsNotRobustAgainst("omission_APlaceMug", "omission_APrepMachine")
    cal.errsNotRobustAgainst("omission_AWait")
  }

  @Test
  fun testTherac() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25.xml"))
    val config = TheracConfig()
    val cal = EOFMRobustCal(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.errsRobustAgainst()
  }

  @Test
  fun testTherac2() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25.xml"))
    val config = TheracConfig()
    val cal = EOFMRobustCal(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.errsNotRobustAgainst("omission_AWaitReady")
    cal.errsNotRobustAgainst("omission_AWaitInPlace", "omission_AWaitReady")
  }

  @Test
  fun testTherac3() {
    val p = ClassLoader.getSystemResource("specs/therac25/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_2.xml"))
    val config = TheracConfig2()
    val cal = EOFMRobustCal(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.errsRobustAgainst()
  }

}
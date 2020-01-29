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
    val transToErr = sm.transitions.filter { it.third == -1 }
    for (t in transToErr) {
      val trace = (paths[t.first] ?: error(t.first)) + t
      println("Length: ${trace.size}")
      println("TRACE = (\n${trace.joinToString(" -> \n") { sm.alphabet[it.second] }} -> END).\n")
    }
  }

  @Test
  fun testCoffee() {
    val p = ClassLoader.getSystemResource("specs/coffee_eofm/p.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/coffee_eofm/machine.lts").readText()
    val coffee: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))

    val cal = EOFMRobustCal(
        sys,
        p,
        coffee,
        mapOf("iBrewing" to "False", "iMugState" to "Absent", "iHandleDown" to "True", "iPodState" to "EmptyOrUsed"),
        listOf(
            "when (iMugState == Absent) hPlaceMug -> VAR[iBrewing][Empty][iHandleDown][iPodState]",
            "when (iMugState != Absent) hTakeMug -> VAR[iBrewing][Absent][iHandleDown][iPodState]",
            "when (iHandleDown == True) hLiftHandle -> VAR[iBrewing][iMugState][False][iPodState]",
            "when (iHandleDown == False) hLowerHandle -> VAR[iBrewing][iMugState][True][iPodState]",
            "when (1) hAddOrReplacePod -> VAR[iBrewing][iMugState][iHandleDown][New]",
            "when (iPodState == New) hPressBrew -> VAR[True][iMugState][iHandleDown][EmptyOrUsed]",
            "when (iPodState != New) hPressBrew -> VAR[True][iMugState][iHandleDown][iPodState]",
            "when (iBrewing == True && iMugState == Empty) mBrewDone -> VAR[False][Full][iHandleDown][iPodState]",
            "when (iBrewing == True && iMugState == Absent) mBrewDone -> VAR[False][iMugState][iHandleDown][iPodState]"
        ),
        relabels = mapOf("hWaitBrewDone" to "mBrewDone")
    )
    cal.run()
  }
}
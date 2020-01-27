import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.propertyCheck
import edu.cmu.isr.ltsa.util.StateMachine
import org.junit.jupiter.api.Test

class ManualTest {
  @Test
  fun genCoffeeHumanTest() {
    val s = ClassLoader.getSystemResource("specs/coffee_eofm/generate_human.lts").readText()
    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(s, "G").doCompose()
    val sm = StateMachine(composite.composition)
    val dfa = sm.tauEliminationAndSubsetConstruct().first
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
}
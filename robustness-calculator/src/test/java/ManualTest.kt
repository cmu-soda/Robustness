import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.util.StateMachine
import org.junit.jupiter.api.Test

class ManualTest {
  @Test
  fun tmpTest() {
    val s = ClassLoader.getSystemResource("specs/coffee_eofm/generate_human.lts").readText()
    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(s, "G").doCompose()
    val sm = StateMachine(composite.composition)
    val dfa = sm.tauEliminationAndSubsetConstruct().first
    print(dfa.minimize().buildFSP("ENV"))
  }
}
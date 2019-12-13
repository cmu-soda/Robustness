import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.EOFMTranslator
import edu.cmu.isr.ltsa.eofm.parseEOFMS
import edu.cmu.isr.ltsa.util.StateMachine
import org.junit.jupiter.api.Test
import java.io.File

class TranslatorTest {
  @Test
  fun testCoffeeEOFM() {
    val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val translator = EOFMTranslator(pca, mapOf("iBrewing" to "False", "iMugState" to "Absent", "iHandleDown" to "True", "iPodState" to "EmptyOrUsed"))

    val builder = StringBuilder()
    val processes = mutableListOf<String>()

    translator.translate(builder, processes)
    File(ClassLoader.getSystemResource("specs/coffee_eofm.lts").toURI()).writeText(builder.toString())

    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(builder.toString()).doCompose()
    val minimized = StateMachine(composite.composition).tauEliminationAndSubsetConstruct().first.minimize().buildFSP("EOFM_MIN")
    File(ClassLoader.getSystemResource("specs/coffee_eofm_min.lts").toURI()).writeText(minimized)
  }

  @Test
  fun testResetEOFM() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/reset.xml"))
    val translator = EOFMTranslator(reset, mapOf("iX" to "False"))

    val builder = StringBuilder()
    val processes = mutableListOf<String>()

    translator.translate(builder, processes)
    File(ClassLoader.getSystemResource("specs/reset_eofm.lts").toURI()).writeText(builder.toString())

    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(builder.toString()).doCompose()
    val minimized = StateMachine(composite.composition).tauEliminationAndSubsetConstruct().first.minimize().buildFSP("EOFM_MIN")
    File(ClassLoader.getSystemResource("specs/reset_eofm_min.lts").toURI()).writeText(minimized)
  }
}
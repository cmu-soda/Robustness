import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.EOFMTranslator2
import edu.cmu.isr.ltsa.eofm.parseEOFMS
import org.junit.jupiter.api.Test
import java.io.File

class TranslatorTest {
  @Test
  fun testCoffeeEOFM() {
    val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val translator = EOFMTranslator2(pca, mapOf("iBrewing" to "False", "iMugState" to "Absent", "iHandleDown" to "True", "iPodState" to "EmptyOrUsed"))

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
    File(ClassLoader.getSystemResource("specs/coffee_eofm.lts").toURI()).writeText(builder.toString())

//    val builder = StringBuilder()
//    val processes = mutableListOf<String>()
//
//    translator.translate(builder, processes)
//    File(ClassLoader.getSystemResource("specs/coffee_eofm.lts").toURI()).writeText(builder.toString())
//
//    val ltsaCall = LTSACall()
//    val composite = ltsaCall.doCompile(builder.toString()).doCompose()
//    val minimized = StateMachine(composite.composition).tauEliminationAndSubsetConstruct().first.minimize().buildFSP("EOFM_MIN")
//    File(ClassLoader.getSystemResource("specs/coffee_eofm_min.lts").toURI()).writeText(minimized)
  }

  @Test
  fun testTiny() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/tiny.xml"))
    val translator = EOFMTranslator2(reset, mapOf("iX" to "False"))

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testOperators() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/operators.xml"))
    val translator = EOFMTranslator2(reset, emptyMap())

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testOmission1() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/omission1.xml"))
    val translator = EOFMTranslator2(reset, mapOf("iX" to "False"))

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testBenchmark() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/benchmark.xml"))
    val translator = EOFMTranslator2(reset, emptyMap())

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }
}
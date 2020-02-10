import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.EOFMTranslator2
import edu.cmu.isr.ltsa.eofm.parseEOFMS
import edu.cmu.isr.ltsa.weakest.EOFMRobustCal
import org.junit.jupiter.api.Test

class TranslatorTest {
  @Test
  fun testCoffeeEOFM() {
    val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val config = CoffeeConfig()
    val translator = EOFMTranslator2(pca, config.initialValues, config.world, config.relabels)
    val builder = StringBuilder()

    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testCoffeeEOFMError() {
    val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val config = CoffeeConfig()
    val translator = EOFMTranslator2(pca, config.initialValues, config.world, config.relabels)
    val builder = StringBuilder()

    translator.translate(builder, withError = true)
    println(builder.toString())
  }

  @Test
  fun testTiny() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/tiny.xml"))
    val translator = EOFMTranslator2(
        reset,
        mapOf("iX" to "False"),
        listOf(
            "when (iX == False) hA -> hB -> VAR[True]",
            "when (iX == False) hB -> hA -> VAR[True]",
            "when (iX == True) restart -> VAR[False]"
        )
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testOperators() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/operators.xml"))
    val translator = EOFMTranslator2(reset, emptyMap(), emptyList())

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testOmission1() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/omission1.xml"))
    val translator = EOFMTranslator2(
        reset,
        mapOf("iX" to "False"),
        listOf(
            "when (iX == False) hA -> hB -> VAR[True]",
            "when (iX == False) hB -> hA -> VAR[True]",
            "when (iX == True) restart -> VAR[False]"
        )
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testBenchmark() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/benchmark.xml"))
    val translator = EOFMTranslator2(reset, emptyMap(), emptyList())

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testTherac() {
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25.xml"))
    val config = TheracConfig()
    val translator = EOFMTranslator2(therac, config.initialValues, config.world, config.relabels)
    val builder = StringBuilder()

    translator.translate(builder)
    println(builder.toString())
  }
}
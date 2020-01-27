import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.EOFMTranslator2
import edu.cmu.isr.ltsa.eofm.parseEOFMS
import org.junit.jupiter.api.Test

class TranslatorTest {
  @Test
  fun testCoffeeEOFM() {
    val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val translator = EOFMTranslator2(
        pca,
        mapOf("iBrewing" to "False", "iMugState" to "Absent", "iHandleDown" to "True", "iPodState" to "EmptyOrUsed"),
        mapOf(
            "iBrewing|iMugState" to listOf(
                Triple("1", "mBrew", "True|iMugState"),
                Triple("iBrewing == True && iMugState == Absent", "mBrewDone", "False|iMugState"),
                Triple("iMugState == Absent", "hPlaceMug", "iBrewing|Empty"),
                Triple("iMugState != Absent", "hTakeMug", "iBrewing|Absent"),
                Triple("iBrewing == True && iMugState == Empty", "mBrewDone", "False|Full")
            ),
            "iBrewing" to listOf(
                Triple("1", "mBrew", "True"),
                Triple("iBrewing == True", "mBrewDone", "False")
            ),
            "iMugState" to listOf(
                Triple("iMugState == Absent", "hPlaceMug", "Empty"),
                Triple("iMugState != Absent", "hTakeMug", "Absent"),
                Triple("iMugState == Empty", "mBrewDone", "Full")
            ),
            "iHandleDown" to listOf(
                Triple("iHandleDown == True", "hLiftHandle", "False"),
                Triple("iHandleDown == False", "hLowerHandle", "True")
            ),
            "iPodState" to listOf(
                Triple("iPodState == New", "mBrew", "EmptyOrUsed"),
                Triple("iPodState == EmptyOrUsed", "hAddOrReplacePod", "New")
            )
        ),
        relabels = mapOf("hWaitBrewDone" to "mBrewDone")
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testCoffeeEOFMError() {
    val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
    val translator = EOFMTranslator2(
        pca,
        mapOf("iBrewing" to "False", "iMugState" to "Absent", "iHandleDown" to "True", "iPodState" to "EmptyOrUsed"),
        mapOf(
            "iBrewing|iMugState" to listOf(
                Triple("1", "mBrew", "True|iMugState"),
                Triple("iBrewing == True && iMugState == Absent", "mBrewDone", "False|iMugState"),
                Triple("iMugState == Absent", "hPlaceMug", "iBrewing|Empty"),
                Triple("iMugState != Absent", "hTakeMug", "iBrewing|Absent"),
                Triple("iBrewing == True && iMugState == Empty", "mBrewDone", "False|Full")
            ),
            "iBrewing" to listOf(
                Triple("1", "mBrew", "True"),
                Triple("iBrewing == True", "mBrewDone", "False")
            ),
            "iMugState" to listOf(
                Triple("iMugState == Absent", "hPlaceMug", "Empty"),
                Triple("iMugState != Absent", "hTakeMug", "Absent"),
                Triple("iMugState == Empty", "mBrewDone", "Full")
            ),
            "iHandleDown" to listOf(
                Triple("iHandleDown == True", "hLiftHandle", "False"),
                Triple("iHandleDown == False", "hLowerHandle", "True")
            ),
            "iPodState" to listOf(
                Triple("iPodState == New", "mBrew", "EmptyOrUsed"),
                Triple("iPodState == EmptyOrUsed", "hAddOrReplacePod", "New")
            )
        ),
        relabels = mapOf("hWaitBrewDone" to "mBrewDone"),
        withError = true
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testTiny() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/tiny.xml"))
    val translator = EOFMTranslator2(
        reset,
        mapOf("iX" to "False"),
        mapOf("iX" to listOf(
            Triple("iX == False", "hA -> hB", "True"),
            Triple("iX == False", "hB -> hA", "True"),
            Triple("iX == True", "restart", "False")
        ))
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testOperators() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/operators.xml"))
    val translator = EOFMTranslator2(reset, emptyMap(), emptyMap())

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
        mapOf("iX" to listOf(
            Triple("iX == False", "hA", "True"),
            Triple("iX == True", "restart", "False")
        ))
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }

  @Test
  fun testBenchmark() {
    val reset: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/benchmark.xml"))
    val translator = EOFMTranslator2(reset, emptyMap(), emptyMap())

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }
}
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
    val translator = EOFMTranslator2(
        therac,
        mapOf("iInterface" to "Edit", "iBeamState" to "NotReady", "iSpreader" to "OutPlace", "iPowerLevel" to "NotSet"),
        listOf(
            "when (iInterface == Edit) hPressX -> VAR[ConfirmXray][iBeamState][iSpreader][iPowerLevel]",
            "when (iInterface == Edit) hPressE -> VAR[ConfirmEBeam][iBeamState][iSpreader][iPowerLevel]",
            "when (iInterface == ConfirmXray || iInterface == ConfirmEBeam) hPressUp -> VAR[Edit][iBeamState][iSpreader][iPowerLevel]",
            "when (iInterface == PrepXray) hPressUp1 -> VAR[ConfirmXray][NotReady][iSpreader][iPowerLevel]",
            "when (iInterface == PrepEBeam) hPressUp1 -> VAR[ConfirmEBeam][NotReady][iSpreader][iPowerLevel]",
            "when (iInterface == ConfirmXray) hPressEnter -> VAR[PrepXray][iBeamState][iSpreader][iPowerLevel]",
            "when (iInterface == ConfirmEBeam) hPressEnter -> VAR[PrepEBeam][iBeamState][iSpreader][iPowerLevel]",
            "when (iInterface == PrepXray || iInterface == PrepEBeam) hPressB -> VAR[Administered][iBeamState][iSpreader][iPowerLevel]",
            "when (iBeamState == NotReady) mBeamReady -> VAR[iInterface][Ready][iSpreader][iPowerLevel]",
            "when (iSpreader == OutPlace) mInPlace -> VAR[iInterface][iBeamState][InPlace][iPowerLevel]",
            "when (iSpreader == InPlace) mOutPlace -> VAR[iInterface][iBeamState][OutPlace][iPowerLevel]",
            "when (iPowerLevel != XrayLevel) mXrayLvl -> VAR[iInterface][iBeamState][iSpreader][XrayLevel]",
            "when (iPowerLevel != EBeamLevel) mEBeamLvl -> VAR[iInterface][iBeamState][iSpreader][EBeamLevel]"
        )
    )

    val builder = StringBuilder()
    translator.translate(builder)
    println(builder.toString())
  }
}
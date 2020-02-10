interface TestConfig {
  val initialValues: Map<String, String>
  val world: List<String>
  val relabels: Map<String, String>
}

class CoffeeConfig : TestConfig {
  override val initialValues: Map<String, String> =
      mapOf("iBrewing" to "False", "iMugState" to "Absent", "iHandleDown" to "True", "iPodState" to "EmptyOrUsed")

  override val world: List<String> =
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
      )

  override val relabels: Map<String, String> = mapOf("hWaitBrewDone" to "mBrewDone")
}

class TheracConfig : TestConfig {
  override val initialValues: Map<String, String> =
      mapOf("iInterface" to "Edit", "iSpreader" to "Unknown", "iPowerLevel" to "NotSet")

  override val world: List<String> =
      listOf(
          "when (iInterface == Edit) hPressX -> VAR[ConfirmXray][iSpreader][iPowerLevel]",
          "when (iInterface == Edit) hPressE -> VAR[ConfirmEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmXray || iInterface == ConfirmEBeam) hPressUp -> VAR[Edit][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray) hPressUp1 -> VAR[ConfirmXray][iSpreader][iPowerLevel]",
          "when (iInterface == PrepEBeam) hPressUp1 -> VAR[ConfirmEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmXray) hPressEnter -> VAR[PrepXray][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmEBeam) hPressEnter -> VAR[PrepEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray || iInterface == PrepEBeam) hPressB -> VAR[Administered][iSpreader][iPowerLevel]",
          "when (iSpreader != InPlace) mInPlace -> VAR[iInterface][InPlace][iPowerLevel]",
          "when (iSpreader != OutPlace) mOutPlace -> VAR[iInterface][OutPlace][iPowerLevel]",
          "when (iPowerLevel != XrayLevel) mXrayLvl -> VAR[iInterface][iSpreader][XrayLevel]",
          "when (iPowerLevel != EBeamLevel) mEBeamLvl -> VAR[iInterface][iSpreader][EBeamLevel]"
      )

  override val relabels: Map<String, String> =
      mapOf(
          "hWaitInPlace" to "mInPlace", "hWaitOutPlace" to "mOutPlace",
          "hWaitXrayPower" to "mXrayLvl", "hWaitEBeamPower" to "mEBeamLvl"
      )
}

class TheracConfig2 : TestConfig {
  override val initialValues: Map<String, String> =
      mapOf("iInterface" to "Edit", "iSpreader" to "Unknown", "iPowerLevel" to "NotSet")

  override val world: List<String> =
      listOf(
          "when (iInterface == Edit) hPressX -> VAR[ConfirmXray][iSpreader][iPowerLevel]",
          "when (iInterface == Edit) hPressE -> VAR[ConfirmEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmXray || iInterface == ConfirmEBeam) hPressUp -> VAR[Edit][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray) hPressUp1 -> VAR[ConfirmXray][iSpreader][iPowerLevel]",
          "when (iInterface == PrepEBeam) hPressUp1 -> VAR[ConfirmEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmXray) hPressEnter -> VAR[PrepXray][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmEBeam) hPressEnter -> VAR[PrepEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray || iInterface == PrepEBeam) hPressB -> VAR[Administered][iSpreader][iPowerLevel]",
          "when (iSpreader != InPlace) mInPlace -> VAR[iInterface][InPlace][iPowerLevel]",
          "when (iSpreader != OutPlace) mOutPlace -> VAR[iInterface][OutPlace][iPowerLevel]",
          "when (iPowerLevel != XrayLevel) mXrayLvl -> VAR[iInterface][iSpreader][XrayLevel]",
          "when (iPowerLevel != EBeamLevel) mEBeamLvl -> VAR[iInterface][iSpreader][EBeamLevel]"
      )

  override val relabels: Map<String, String> = emptyMap()
}
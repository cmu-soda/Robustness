/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package edu.cmu.isr.robust.eofm

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

class TheracWaitConfig : TestConfig {
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

class TheracNoWaitConfig : TestConfig {
  override val initialValues: Map<String, String> =
      mapOf("iInterface" to "Edit", "iSpreader" to "OutPlace", "iPowerLevel" to "NotSet")

  override val world: List<String> =
      listOf(
          "when (iInterface == Edit) hPressX -> VAR[ConfirmXray][InPlace][iPowerLevel]",
          "when (iInterface == Edit) hPressE -> VAR[ConfirmEBeam][OutPlace][iPowerLevel]",
          "when (iInterface == ConfirmXray || iInterface == ConfirmEBeam) hPressUp -> VAR[Edit][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray) hPressUp1 -> VAR[ConfirmXray][iSpreader][iPowerLevel]",
          "when (iInterface == PrepEBeam) hPressUp1 -> VAR[ConfirmEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmXray) hPressEnter -> VAR[PrepXray][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmEBeam) hPressEnter -> VAR[PrepEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray || iInterface == PrepEBeam) hPressB -> VAR[Administered][iSpreader][iPowerLevel]",
          "when (iPowerLevel == EBeamLevel) mXrayLvl -> VAR[iInterface][iSpreader][XrayLevel]",
          "when (iPowerLevel == XrayLevel) mEBeamLvl -> VAR[iInterface][iSpreader][EBeamLevel]",
          "when (iPowerLevel == NotSet) mInitXray -> VAR[iInterface][iSpreader][XrayLevel]",
          "when (iPowerLevel == NotSet) mInitEBeam -> VAR[iInterface][iSpreader][EBeamLevel]"
      )

  override val relabels: Map<String, String> = emptyMap()
}

class TheracRobustNoWaitConfig : TestConfig {
  override val initialValues: Map<String, String> =
      mapOf("iInterface" to "Edit", "iSpreader" to "OutPlace", "iPowerLevel" to "NotSet")

  override val world: List<String> =
      listOf(
          "when (iInterface == Edit) hPressX -> VAR[ConfirmXray][InPlace][iPowerLevel]",
          "when (iInterface == Edit) hPressE -> VAR[ConfirmEBeam][OutPlace][iPowerLevel]",
          "when (iInterface == ConfirmXray || iInterface == ConfirmEBeam) hPressUp -> VAR[Edit][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray) hPressUp1 -> VAR[ConfirmXray][iSpreader][iPowerLevel]",
          "when (iInterface == PrepEBeam) hPressUp1 -> VAR[ConfirmEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmXray) hPressEnter -> VAR[PrepXray][iSpreader][iPowerLevel]",
          "when (iInterface == ConfirmEBeam) hPressEnter -> VAR[PrepEBeam][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray && iSpreader == InPlace && iPowerLevel == XrayLevel) hPressB -> VAR[Administered][iSpreader][iPowerLevel]",
          "when (iInterface == PrepEBeam && iSpreader == OutPlace && iPowerLevel == EBeamLevel) hPressB -> VAR[Administered][iSpreader][iPowerLevel]",
          "when (iInterface == PrepXray && !(iSpreader == InPlace && iPowerLevel == XrayLevel)) hPressB -> VAR[iInterface][iSpreader][iPowerLevel]",
          "when (iInterface == PrepEBeam && !(iSpreader == OutPlace && iPowerLevel == EBeamLevel)) hPressB -> VAR[iInterface][iSpreader][iPowerLevel]",
          "when (iPowerLevel == EBeamLevel) mXrayLvl -> VAR[iInterface][iSpreader][XrayLevel]",
          "when (iPowerLevel == XrayLevel) mEBeamLvl -> VAR[iInterface][iSpreader][EBeamLevel]",
          "when (iPowerLevel == NotSet) mInitXray -> VAR[iInterface][iSpreader][XrayLevel]",
          "when (iPowerLevel == NotSet) mInitEBeam -> VAR[iInterface][iSpreader][EBeamLevel]"
      )

  override val relabels: Map<String, String> = emptyMap()
}
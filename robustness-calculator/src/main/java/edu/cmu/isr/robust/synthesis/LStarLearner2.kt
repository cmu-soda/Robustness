/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang, David Garlan, Eunsuk Kang
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

package edu.cmu.isr.robust.synthesis

import edu.cmu.isr.robust.ltsa.*
import edu.cmu.isr.robust.util.SimpleTransitions
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Trace

class LStarLearner2(private val sys: String, private val dev: String, private val p: String, private val spec: String) {

  private val alphabetSys: Set<String>
  private val alphabetInput: List<String>
  private val alphabetOutput: List<String>
  private val nameSys: String
  private val nameDev: String
  private val nameP: String
  private val nameSpec: String
  private val prunedSys: String
//  private val errInputTraces: String

  init {
    val sysComp = LTSACall.doCompile(sys)
    alphabetSys = sysComp.alphabetNoTau()
    nameSys = sysComp.getCompositeName()
    alphabetInput = LTSACall.menuActions("INPUT_ACTS")
    alphabetOutput = LTSACall.menuActions("OUTPUT_ACTS")

    val devComp = LTSACall.doCompile(dev)
    nameDev = devComp.getCompositeName()

    val pComp = LTSACall.doCompile(p)
    nameP = pComp.getCompositeName()

    val specComp = LTSACall.doCompile(spec)
    nameSpec = specComp.getCompositeName()

    val sm = StateMachine(
//      LTSACall.doCompile("$sys$dev$p||C = ($nameSys || $nameDev || $nameP).", "C").doCompose()
      LTSACall.doCompile("$sys$p||C = ($nameSys || $nameP).", "C").doCompose()
    )
    // A pruned machine
    val trans = sm.transitions.filter { it.third != -1 }
    prunedSys = StateMachine(SimpleTransitions(trans), sm.alphabet).buildFSP("PRUNED")

//    val errTraces = sm.transitions
//      .filter { it.third == -1 && sm.alphabet[it.second] in alphabetInput }
//      .map { sm.pathFromInit(it.first) + sm.alphabet[it.second] }
//    errInputTraces = "ERR_INPUTS = (${
//      errTraces.joinToString(" | ") { it.joinToString(" -> ") + " -> END" }
//    })+{${alphabetSys.joinToString(",")}}."
  }

  fun synthesize(): String? {
    try {
      val lstar = LStar(alphabetSys.toList(), ::mqOracle, ::eqOracle)
      val spec = lstar.lstar()
      println(spec)
      return spec
    } catch (e: NoRefinementException) {
      System.err.println(e.message)
      System.err.println("At least one of the following queries does no hold:")
      for (q in queryStrings(e.trace)) {
        System.err.println(q)
        System.err.println("-----------------------")
      }
    }
    return null
  }

  /**
   * In membership query, we are removing traces from the hypothesis model:
   * 1. Query |= Spec, remove the trace that violates the Spec
   * 2. Query || Dev |= P, remove the trace that does not violates the Spec but violates the property under Dev
   */
  private fun mqOracle(query: Trace): Boolean {
    for (q in queryStrings(query)) {
      if (LTSACall.doCompile(q, "C").doCompose().propertyCheck() != null)
        return false
    }
    return true
//
//    val oracle1 = LTSACall.doCompile("$queryTrace$spec||C = (Q || $nameSpec).", "C")
//      .doCompose().propertyCheck() == null
//    val oracle2 = LTSACall.doCompile("$queryTrace$dev$p||C = (Q || $nameDev || $nameP).", "C")
//      .doCompose().propertyCheck() == null
//    return oracle1 && oracle2
  }

  private fun queryStrings(query: Trace): List<String> {
    val queryTrace = if (query.isEmpty())
      "Q = STOP+{${alphabetSys.joinToString(", ")}}."
    else
      "Q = (${query.joinToString(" -> ")} -> STOP)+{${alphabetSys.joinToString(", ")}}."
    return listOf(
      "$queryTrace\n$spec\n||C = (Q || $nameSpec).",
      "$queryTrace\n$dev\n$p\n||C = (Q || $nameDev || $nameP)."
    )
  }

  /**
   * The hypothesis model may be too small (e.g., an empty model). Thus, in the equivalence check, we are adding traces
   * back to the hypothesis model by counterexamples.
   * 1. Hypothesis |= Spec
   * 2. Hypothesis || Dev |= P
   * 3. PruneError(Sys || Dev || P_err) |= Hypothesis, for those traces in Sys that will not violate P under deviation
   *    Dev, they should be in the Hypothesis model.
   */
  private fun eqOracle(hypothesis: String): Trace? {
    val oracles = listOf(
      "$hypothesis$spec||C = (H || $nameSpec).",
      "$hypothesis$dev$p||C = (H || $nameDev || $nameP).",
      "${prunedSys}property ${hypothesis}||C = (PRUNED || H)."
    )

    for ((i, oracle) in oracles.withIndex()) {
      val counterexample = LTSACall.doCompile(oracle, "C").doCompose().propertyCheck()
      if (counterexample != null) {
        println("Find counterexample from oracle ${i+1}: $counterexample")
        return counterexample
      }
    }

    return null
  }

}

fun main() {
//  val sys = "SENDER = (input -> send -> getack -> SENDER).\n" +
//      "RECEIVER = (rec -> output -> ack -> RECEIVER).\n" +
//      "||ENV = (SENDER || RECEIVER)."
//
//  val env = "CHANNEL = (in -> out -> CHANNEL).\n" +
//      "||ENV = (trans:CHANNEL || recv:CHANNEL)/{send/trans.in, rec/trans.out, ack/recv.in, getack/recv.out}."
//
//  val envLossy = "CHANNEL = (in -> TRANSIT\n" +
//      "         | in -> lose -> CHANNEL),\n" +
//      "TRANSIT = (out -> CHANNEL | out -> duplicate -> TRANSIT).\n" +
//      "||ENV = (trans:CHANNEL || recv:CHANNEL)/{send/trans.in, rec/trans.out, ack/recv.in, getack/recv.out}."
//
//  val p = "property P = (input -> output -> P)."
  val sys = ClassLoader.getSystemResource("models/therac25-simple/sys.lts").readText()
  val env = "ENV = (hPressX -> ENV_1 | hPressE -> ENV_1),\n" +
      "ENV_1 = (hPressEnter -> ENV_2 | hPressUp -> ENV),\n" +
      "ENV_2 = (hPressB -> mFire -> hPressEnter -> ENV)+{hPressUp1}."
  val p = ClassLoader.getSystemResource("models/therac25-simple/p_w.lts").readText()
  val spec = "property SPEC1 = (hPressX -> mInPlace -> SPEC1 | hPressE -> mOutPlace -> SPEC1).\n" +

      "property SPEC2 = (hPressX -> mInitXray -> SPEC2_1 | hPressE -> mInitEBeam -> SPEC2_1)," +
      "SPEC2_1 = (hPressX -> SPEC2_1 | hPressE -> SPEC2_1).\n" +

      "property SPEC4 = (hPressB -> SPEC4_1), SPEC4_1 = (hPressB -> SPEC4_1 | mFire -> SPEC4).\n" +

//      "property SPEC5 = (mInitXray -> SPEC5_1 | mInitEBeam -> SPEC5_1), SPEC5_1 = (hPressX -> SPEC5_2)," +
//      "SPEC5_2 = (hPressX -> SPEC5_2 | mXrayLvl -> SPEC5_1).\n" +
//
//      "property SPEC6 = (mInitXray -> SPEC6_1 | mInitEBeam -> SPEC6_1), SPEC6_1 = (hPressE -> SPEC6_2)," +
//      "SPEC6_2 = (hPressE -> SPEC6_2 | mEBeamLvl -> SPEC6_1).\n" +

      "property POWER = (hPressX -> mInitXray -> XRAY | hPressE -> mInitEBeam -> EBEAM),\n" +
      "TOXRAY = (hPressX -> TOXRAY | hPressE -> EBEAM | mXrayLvl -> XRAY),\n" +
      "TOEBEAM = (hPressX -> XRAY | hPressE -> TOEBEAM | mEBeamLvl -> EBEAM),\n" +
      "XRAY = (hPressX -> XRAY | hPressE -> TOEBEAM),\n" +
      "EBEAM = (hPressE -> EBEAM | hPressX -> TOXRAY)." +

//      "property SPEC7 = (mInitXray -> mReady -> SPEC7_1 | mInitEBeam -> mReady -> SPEC7_1)," +
//      "SPEC7_1 = (mXrayLvl -> mReady -> SPEC7_1 | mEBeamLvl -> mReady -> SPEC7_1)." +

      "||SPEC = (SPEC1 || SPEC2 || SPEC4 || POWER)."
  val learner = LStarLearner2(sys, env, p, spec)
  learner.synthesize()
}
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
import edu.cmu.isr.robust.wa.Corina02WithIO

@Deprecated("This is a deprecated attempt using L* to robustify a system.")
class LStarLearner2(
  private val sys: String,
  private val env: String,
  private val dev: String,
  private val p: String,
  private val spec: String
) {

  private val alphabetSys: Set<String>
  private val alphabetInput: List<String>
  private val alphabetOutput: List<String>
  private val nameSys: String
  private val nameEnv: String
  private val nameDev: String
  private val nameP: String
  private val nameSpec: String
  private val sysRobustness: String
//  private val prunedSys: String

  init {
    val sysComp = LTSACall.doCompile(sys)
    alphabetSys = sysComp.alphabetNoTau()
    nameSys = sysComp.getCompositeName()
    alphabetInput = LTSACall.menuActions("INPUT_ACTS")
    alphabetOutput = LTSACall.menuActions("OUTPUT_ACTS")
    sysRobustness = Corina02WithIO(sys, dev, p, alphabetInput, alphabetOutput).weakestAssumption("SYS_R")

    val envComp = LTSACall.doCompile(env)
    nameEnv = envComp.getCompositeName()

    val devComp = LTSACall.doCompile(dev)
    nameDev = devComp.getCompositeName()

    val pComp = LTSACall.doCompile(p)
    nameP = pComp.getCompositeName()

    val specComp = LTSACall.doCompile(spec)
    nameSpec = specComp.getCompositeName()

//    val sm = StateMachine(
//      LTSACall.doCompile("$sys$p||C = ($nameSys || $nameP).", "C").doCompose()
//    )
//    // A pruned machine
//    val trans = sm.transitions.filter { it.third != -1 }
//    prunedSys = StateMachine(SimpleTransitions(trans), sm.alphabet).buildFSP("PRUNED")
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
   * 3. Query || RobustnessModel(Sys, Env, P) |= P, remove the trace that is also in the robustness of the original
   *    system but violates P
   */
  private fun mqOracle(query: Trace): Boolean {
    for (q in queryStrings(query)) {
      if (LTSACall.doCompile(q, "C").doCompose().propertyCheck() != null)
        return false
    }
    return true
  }

  private fun queryStrings(query: Trace): List<String> {
    val queryTrace = if (query.isEmpty())
      "Q = STOP+{${alphabetSys.joinToString(", ")}}."
    else
      "Q = (${query.joinToString(" -> ")} -> STOP)+{${alphabetSys.joinToString(", ")}}."
    return listOf(
      "$queryTrace\n$spec\n||C = (Q || $nameSpec).",
      "$queryTrace\n$dev\n$p\n||C = (Q || $nameDev || $nameP).",
      "$queryTrace\n$sysRobustness\n$p\n||C = (Q || SYS_R || $nameP)."
    )
  }

  /**
   * 1. Safety: Hypothesis |= Spec
   * 2. Safety: Hypothesis || Dev |= P
   * 3. Safety: Hypothesis || RobustnessModel(Sys, Env, P) |= P
   * 4. Liveness: Sys || Env |= Hypothesis
   */
  private fun eqOracle(hypothesis: String): Trace? {
    val oracles = listOf(
      "$hypothesis$spec||C = (H || $nameSpec).",
      "$hypothesis$dev$p||C = (H || $nameDev || $nameP).",
      "$hypothesis$sysRobustness$p||C = (H || SYS_R || $nameP).",
      "$sys${env}property ${hypothesis}||C = ($nameSys || $nameEnv || H)."
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
//  val sys = "menu INPUT_ACTS = {getack}\n" +
//      "menu OUTPUT_ACTS = {send}\n" +
//      "SENDER = (input -> send -> getack -> SENDER)."
//  val env = "RECEIVER = (rec -> output -> ack -> RECEIVER).\n" +
//      "CHANNEL = (in -> out -> CHANNEL).\n" +
//      "||ENV = (trans:CHANNEL || recv:CHANNEL || RECEIVER)/{send/trans.in, rec/trans.out, ack/recv.in, getack/recv.out}."
//  val dev = "CHANNEL = (in -> TRANSIT\n" +
//      "         | in -> lose -> CHANNEL),\n" +
//      "TRANSIT = (out -> CHANNEL | out -> duplicate -> TRANSIT).\n" +
//      "||ENV = (trans:CHANNEL || recv:CHANNEL)/{send/trans.in, rec/trans.out, ack/recv.in, getack/recv.out}."
//  val p = "property P = (input -> output -> P)."
//  val spec = "property SPEC = (dummy -> SPEC)."

//  val sys = ClassLoader.getSystemResource("models/therac25-simple/sys.lts").readText()
//  val env = ClassLoader.getSystemResource("models/therac25-simple/env.lts").readText()
//  val dev = "ENV = (hPressX -> ENV_1 | hPressE -> ENV_1),\n" +
//      "ENV_1 = (hPressEnter -> ENV_2 | hPressUp -> ENV),\n" +
//      "ENV_2 = (hPressB -> mFire -> hPressEnter -> ENV)+{hPressUp1}."
//  val p = ClassLoader.getSystemResource("models/therac25-simple/p_w.lts").readText()
//  val spec = "property SPEC1 = (hPressX -> mInPlace -> SPEC1 | hPressE -> mOutPlace -> SPEC1).\n" +
//
//      "property SPEC2 = (hPressX -> mInitXray -> SPEC2_1 | hPressE -> mInitEBeam -> SPEC2_1)," +
//      "SPEC2_1 = (hPressX -> SPEC2_1 | hPressE -> SPEC2_1).\n" +
//
//      "property SPEC4 = (hPressB -> SPEC4_1), SPEC4_1 = (hPressB -> SPEC4_1 | mFire -> SPEC4).\n" +
//
//      "property POWER = (hPressX -> mInitXray -> XRAY | hPressE -> mInitEBeam -> EBEAM),\n" +
//      "TOXRAY = (hPressX -> TOXRAY | hPressE -> EBEAM | mXrayLvl -> XRAY),\n" +
//      "TOEBEAM = (hPressX -> XRAY | hPressE -> TOEBEAM | mEBeamLvl -> EBEAM),\n" +
//      "XRAY = (hPressX -> XRAY | hPressE -> TOEBEAM),\n" +
//      "EBEAM = (hPressE -> EBEAM | hPressX -> TOXRAY)." +
//
//      "||SPEC = (SPEC1 || SPEC2 || SPEC4 || POWER)."

  val sys = "menu INPUT_ACTS = {hPressX, hPressE, hPressUp, hPressEnter, hPressB, hPressUp1}\n" +
      "menu OUTPUT_ACTS = {mFire}\n" +
      "\n" +
      "INTERFACE = (hPressX -> mInPlace -> mInitXray -> CONFIRM | hPressE -> mOutPlace -> mInitEBeam -> CONFIRM),\n" +
      "EDIT = (hPressX -> mInPlace -> CONFIRM | hPressE -> mOutPlace -> CONFIRM),\n" +
      "CONFIRM = (hPressUp -> EDIT | hPressEnter -> PREP),\n" +
      "PREP = (hPressB -> FIRE | hPressUp1 -> CONFIRM),\n" +
      "FIRE = (mFire -> hPressEnter -> EDIT)."
  val env = "ENV = (hPressX -> ENV_1 | hPressE -> ENV_1),\n" +
      "ENV_1 = (hPressEnter -> ENV_2),\n" +
      "ENV_2 = (hPressB -> mFire -> END)+{hPressUp, hPressUp1}.\n" +
      "POWER = (hPressX -> mInitXray -> XRAY | hPressE -> mInitEBeam -> EBEAM),\n" +
      "TOXRAY = (hPressX -> TOXRAY | hPressE -> EBEAM | mXrayLvl -> XRAY),\n" +
      "TOEBEAM = (hPressX -> XRAY | hPressE -> TOEBEAM | mEBeamLvl -> EBEAM),\n" +
      "XRAY = (hPressX -> XRAY | hPressE -> TOEBEAM),\n" +
      "EBEAM = (hPressE -> EBEAM | hPressX -> TOXRAY).\n" +
      "||ENV_PWR = (ENV || POWER)."
  val dev = "ENV = (hPressX -> ENV_1 | hPressE -> ENV_1),\n" +
      "ENV_1 = (hPressEnter -> ENV_2 | hPressUp -> ENV),\n" +
      "ENV_2 = (hPressB -> mFire -> hPressEnter -> ENV)+{hPressUp1}.\n" +
      "POWER = (hPressX -> mInitXray -> XRAY | hPressE -> mInitEBeam -> EBEAM),\n" +
      "TOXRAY = (hPressX -> TOXRAY | hPressE -> EBEAM | mXrayLvl -> XRAY),\n" +
      "TOEBEAM = (hPressX -> XRAY | hPressE -> TOEBEAM | mEBeamLvl -> EBEAM),\n" +
      "XRAY = (hPressX -> XRAY | hPressE -> TOEBEAM),\n" +
      "EBEAM = (hPressE -> EBEAM | hPressX -> TOXRAY).\n" +
      "||DEV_PWR = (ENV || POWER)."
  val p = ClassLoader.getSystemResource("models/therac25-simple/p_w.lts").readText()
  val spec = "property SPEC1 = (hPressX -> mInPlace -> SPEC1 | hPressE -> mOutPlace -> SPEC1).\n" +

      "property SPEC2 = (hPressX -> mInitXray -> SPEC2_1 | hPressE -> mInitEBeam -> SPEC2_1)," +
      "SPEC2_1 = (hPressX -> SPEC2_1 | hPressE -> SPEC2_1).\n" +

      "property SPEC4 = (hPressB -> SPEC4_1), SPEC4_1 = (hPressB -> SPEC4_1 | mFire -> SPEC4).\n" +

      "||SPEC = (SPEC1 || SPEC2 || SPEC4)."

  val learner = LStarLearner2(sys, env, dev, p, spec)
  learner.synthesize()
}
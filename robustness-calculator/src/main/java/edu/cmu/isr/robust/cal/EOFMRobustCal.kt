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

package edu.cmu.isr.robust.cal

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.combineSpecs
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.eofm.Action
import edu.cmu.isr.robust.eofm.Activity
import edu.cmu.isr.robust.eofm.EOFMS
import edu.cmu.isr.robust.eofm.EOFMTranslator
import edu.cmu.isr.robust.ltsa.buildTrace
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Trace

class EOFMRobustCal private constructor(
    sys: String,
    p: String,
    private val rawHumanModel: String,
    private val conciseHumanModel: String,
    private val translator: EOFMTranslator,
    verbose: Boolean
) : AbstractRobustCal(sys, conciseHumanModel, p, verbose) {

  companion object {
    @JvmStatic
    fun create(sys: String, p: String, human: EOFMS, initState: Map<String, String>,
               world: List<String>, relabels: Map<String, String>, verbose: Boolean = true): EOFMRobustCal
    {
      println("Generating the LTSA spec of the EOFM human model...")
      val translator = EOFMTranslator(human, initState, world, relabels)
      val rawModel = genHumanModel(translator)
      val conciseModel = genConciseHumanModel(translator, sys, rawModel)
      if (verbose) {
        println("Translated LTSA spec:")
        println(conciseModel)
      }
      return EOFMRobustCal(sys, p, rawModel, conciseModel, translator, verbose)
    }

    /**
     * Translate the human behavior model in EOFM to FSP.
     */
    @JvmStatic
    private fun genHumanModel(translator: EOFMTranslator): String {
      val builder = StringBuilder()
      translator.translate(builder)
      return builder.toString()
    }

    /**
     * Generate the concise FSP spec for the human behavior by composing the translated EOFM model with the
     * system model.
     */
    @JvmStatic
    private fun genConciseHumanModel(translator: EOFMTranslator, sys: String, rawModel: String): String {
      val actions = translator.getActions()

      val rawComposite = LTSACall.doCompile(rawModel, "ENV").doCompose()
      println("Translated EOFM LTS: ${rawComposite.composition.maxStates} states and ${rawComposite.composition.ntransitions()} transitions.")

      val spec = combineSpecs(rawModel, sys, "||G = (SYS || ENV)@{${actions.joinToString(", ")}}.")
      val composite = LTSACall.doCompile(spec, "G").doCompose()
      val conciseHuman = StateMachine(composite).tauElmAndSubsetConstr().first
      return conciseHuman.minimize().buildFSP("ENV")
    }

    /**
     * Generate the human error model (deviation model).
     */
    @JvmStatic
    private fun genHumanErrModel(translator: EOFMTranslator): String {
      val builder = StringBuilder()
      translator.translate(builder, withError = true)
      return builder.toString()
    }

    /**
     * Generate the human error model (deviation model) containing only certain human errors.
     */
    @JvmStatic
    private fun genHumanErrModel(translator: EOFMTranslator, errs: List<String>): String {
      val builder = StringBuilder()
      translator.translate(builder, errs = errs)
      return builder.toString()
    }
  }

  override fun genErrEnvironment(t: Trace): String? {
    // Match the normative prefix with the human model to get activity states
    val normPrefix = t.subList(0, t.size - 1)
    val activityStates = if (normPrefix.isNotEmpty())
      matchNormTrace(normPrefix).filter { !isEnvEvent(it) } else emptyList()
    val path = translator.pathToAction(t.last())
    val errs = matchErrors(path.reversed(), activityStates)
    if (verbose)
      println("Potential errors: $errs")
    return genHumanErrModel(translator, errs.toList())
  }

  override fun isEnvEvent(a: String): Boolean {
    return !isErrEvent(a) && !a.startsWith("start") && !a.startsWith("end") &&
        !a.startsWith("repeat") && !a.startsWith("reset") && !a.startsWith("skip")
  }

  override fun isErrEvent(a: String): Boolean {
    return a.startsWith("omission") || a.startsWith("commission") || a.startsWith("repetition")
  }

  private fun matchNormTrace(t: Trace): Trace {
    val tSpec = buildTrace(t, waGenerator.alphabetOfWA())
    val spec = combineSpecs(rawHumanModel, sys, tSpec, "||T = (SYS || ENV || TRACE).")
    val composite = LTSACall.doCompile(spec, "T").doCompose()
    val sm = StateMachine(composite)
    return sm.pathFromInit(-1)
  }

  /**
   * @ensures path is from the last activity directly connected to the action to the root activity.
   */
  private fun matchErrors(path: List<Activity>, states: List<String>): Set<String> {
    val errs = mutableSetOf<String>()
    var i = 0
    var substates = states
    while (i < path.size) {
      val a = path[i]
      val s = lastStateOfActivity(substates, a)
      if (s == null || s.startsWith("end_")) { // the activity has not started or ended
        errs.add("commission_${translator.translatedName(a)}")
      } else { // the activity has started or repeated
        errs.add("repetition_${translator.translatedName(a)}")
        errs.add("commission_${translator.translatedName(a)}")
        break
      }
      i++
    }
    i = if (i == path.size) i - 1 else i
    while (i > 0) {
      val a = path[i]
      val idx = lastStateIndexOfActivity(substates, a)
      substates = substates.subList(if (idx == -1) 0 else idx, substates.size)

      val op = a.decomposition.operator
      val subs = a.decomposition.subActivities
      // If op is 'ord', then all the siblings before this activity should have already ended or have omission error.
      if (op == "ord") {
        var j = 0
        while (j < subs.size) {
          val s = lastStateOfActivity(substates, subs[j] as Activity)
          if (s != null && (s.startsWith("start_") || s.startsWith("repeat_")))
            break
          j++
        }
        var k = subs.indexOf(path[i-1])
        k = if (j > k) k + subs.size else k
        while (j < k) {
          val sub = subs[j % subs.size] as Activity
          val s = lastStateOfActivity(substates, sub)
          errs.add("omission_${translator.translatedName(sub)}")
          if (s != null && (s.startsWith("start_") || s.startsWith("repeat_"))) {
            errs.addAll(matchOmissions(sub, substates.subList(substates.indexOf(s), substates.size)))
          }
          j++
        }
      } else {
        for (sub in subs) {
          val s = lastStateOfActivity(substates, sub as Activity)
          if (s != null && (s.startsWith("start_") || s.startsWith("repeat_"))) {
            errs.add("omission_${translator.translatedName(sub)}")
            errs.addAll(matchOmissions(sub, substates.subList(substates.indexOf(s), substates.size)))
          }
        }
      }
      i--
    }

    return errs
  }

  /**
   * @ensures activity should already started
   */
  private fun matchOmissions(activity: Activity, states: List<String>): Set<String> {
    val errs = mutableSetOf<String>()
    val op = activity.decomposition.operator
    val subs = activity.decomposition.subActivities
    if (op == "ord" && subs.first() is Action)
      return errs
    // if the decomposition operator for the current node is 'and' or 'ord', then all its children should either
    // already ended or have omission error.
    if (op.startsWith("and") || op == "ord") {
      for (sub in subs) {
        val s = lastStateOfActivity(states, sub as Activity)
        if (s == null)  // the sub-activity has not started
          errs.add("omission_${translator.translatedName(sub)}")
        else if (s.startsWith("start_") || s.startsWith("repeat_")) { // has started but not ended
          errs.add("omission_${translator.translatedName(sub)}")
          errs.addAll(matchOmissions(sub, states.subList(states.indexOf(s), states.size)))
        }
      }
    }

    return errs
  }

  /**
   *
   */
  private fun lastStateOfActivity(states: List<String>, activity: Activity): String? {
    return states.findLast { it.endsWith(translator.translatedName(activity)) }
  }

  /**
   *
   */
  private fun lastStateIndexOfActivity(states: List<String>, activity: Activity): Int {
    return states.indexOfLast { it.endsWith(translator.translatedName(activity)) }
  }

}
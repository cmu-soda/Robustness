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

import edu.cmu.isr.robust.ltsa.*
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.wa.AbstractWAGenerator
import edu.cmu.isr.robust.wa.Corina02
import java.util.*

abstract class AbstractRobustCal(val sys: String, val env: String, val p: String, val verbose: Boolean = true) {

  /**
   * A helper class used in BFS search.
   * @param s the current state of the state machine
   * @param t the action leads to this state
   * @param pre previous node in the search
   */
  private class Node(val s: Int, val t: String, val pre: Node?)

  /**
   * The name of the process of the generated weakest assumption FSP model
   */
  var nameOfWA = "WA"
    set(value) {
      if (wa != null)
        println("WARN: The weakest assumption has already generated, cannot change its name.")
      else
        field = value
    }

  /**
   * The weakest assumption generator. By default, we are using the Corina02 paper to generate it.
   */
  protected val waGenerator: AbstractWAGenerator = Corina02(sys, env, p)

  /**
   * To store the generated weakest assumption
   */
  private var wa: String? = null

  /**
   * Return the FSP spec of the weakest assumption
   */
  fun getWA(): String {
    return wa ?: genWeakestAssumption()
  }

  private fun genWeakestAssumption(): String {
    // Check that SYS||ENV |= P. Our tool assumes that the system should already satisfy the property under the
    // original environment model. Thus, we check this assumption here first.
    val spec = combineSpecs(sys, env, p, "||T = (SYS || ENV || P).")
    val errs = LTSACall.doCompile(spec, "T").doCompose().propertyCheck()
    if (errs != null) {
      println("ERROR: SYS || ENV |= P does not hold, property violation or deadlock:\n\t${errs.joinToString("\n\t")}\n")
    }

    println("Alphabet for weakest assumption: ${waGenerator.alphabetOfWA()}")
    println("Generating the weakest assumption...")
    wa = waGenerator.weakestAssumption(nameOfWA)
    if (verbose) {
      println("Generated Weakest Assumption:")
      println(wa)
    }
    return wa!!
  }

  /**
   * Compute the set of traces allowed by the system but would violate the safety property.
   */
  fun computeUnsafeBeh(): List<List<String>> {
    val corina02 = waGenerator as? Corina02 ?: error("This function is only supported by the Corina02 approach")
    val traces = corina02.computeUnsafeBeh()
    printRepTraces(traces)
    return traces.values.flatten()
  }

  /**
   * The entrance function to compute the robustness. It first generates the weakest assumption, and then build the
   * representation model and compute the representative traces, and finally, match an explanation for each
   * representative trace respectively.
   */
  fun computeRobustness(level: Int = -1): List<Pair<List<String>, List<String>?>> {
    if (wa == null)
      genWeakestAssumption()

    val traces = if (level == -1) {
      println("Generating the representation traces by equivalence classes...")
      waGenerator.shortestDeltaTraces(wa!!, nameOfWA)
    } else {
      println("Generating the level $level representation traces by equivalence classes...")
      waGenerator.deltaTraces(wa!!, nameOfWA, level = level)
    }

    if (traces.isEmpty()) {
      println("No representation traces found. The weakest assumption has equal or less behavior than the environment.")
      return emptyList()
    }
    printRepTraces(traces)

    // Match each deviation trace back to the deviation model
    println("Generating explanations for the representation traces...")
    val explanation = traces.values.flatten().mapIndexed { i, v ->
      if (verbose)
        println("Matching the Representative Trace No.$i '$v' to the deviation model...")
      val exp = matchMinimalErr(v)
      if (verbose) {
        if (exp != null) {
          println("\t${exp.joinToString("\n\t")}\n")
        } else {
          println("ERROR: No explanation found.\n")
        }
      }
      Pair(v, exp)
    }
    printExplanation(explanation)
    return explanation
  }

  /**
   * Helper function to print all the representative traces
   */
  private fun printRepTraces(traces: Map<AbstractWAGenerator.EquivClass, List<List<String>>>) {
    println("Number of equivalence classes: ${traces.size}")
    traces.values.flatten().forEachIndexed { i, v ->
      println("Representation Trace No.$i: $v")
    }
    println()
  }

  /**
   * Helper function to print all the <representative trace, explanation> pair.
   */
  private fun printExplanation(r: List<Pair<List<String>, List<String>?>>) {
    println("Found ${r.size} representation traces, matched ${r.filter { it.second != null }.size}/${r.size}.")
    println("Group by error types in the deviation model:")
    val grouped = r.groupBy {
      val explanation = it.second
      explanation?.filter { a -> isErrEvent(a) }?.joinToString(",") ?: "Unexplained"
    }
    for ((k, v) in grouped) {
      println("Group: '$k', Number of traces: ${v.size}")
    }
  }

  /**
   * The entrance function to compare the robustness of this model to another model, i.e., X = \Delta_This - \Delta_2.
   * @param wa2 the FSP spec of the weakest assumption of the other model
   * @param name2 the name of the process of the weakest assumption.
   */
  fun robustnessComparedTo(wa2: String, name2: String, level: Int = -1): List<List<String>> {
    if (wa == null)
      genWeakestAssumption()

    val traces = if (level == -1) {
      println("Generating the representation traces by equivalence classes...")
      waGenerator.shortestDeltaTraces(wa!!, nameOfWA, wa2, name2)
    } else {
      println("Generating the level $level representation traces by equivalence classes...")
      waGenerator.deltaTraces(wa!!, nameOfWA, wa2, name2, level = level)
    }

    if (traces.isEmpty()) {
      println("No representation traces found. The weakest assumption of M1 has equal or less behavior than the weakest assumption of M2.")
      return emptyList()
    }
    printRepTraces(traces)

    return traces.values.flatten()
  }

  /**
   * Return the deviation model according to a given representative trace. A representative trace is provided in the
   * case that the deviation model is dynamically built based on that trace. For example, in the EOFM example, we
   * build a deviation model only contains errors related to the representative trace.
   * @param t the representative trace
   */
  abstract fun genErrEnvironment(t: List<String>): String

  /**
   * Returns True when the given action is an environmental action.
   */
  abstract fun isEnvEvent(a: String): Boolean

  /**
   * Returns True when the given action is an error/deviation action.
   */
  abstract fun isErrEvent(a: String): Boolean

  /**
   * The entrance function to match the representative trace to the shortest explanation in the deviation model.
   */
  private fun matchMinimalErr(trace: List<String>): List<String>? {
    val errEnv = genErrEnvironment(trace)
    val tSpec = buildTrace(trace, waGenerator.alphabetOfWA())
    val spec = combineSpecs(sys, errEnv, tSpec, "||T = (SYS || ENV || TRACE).")
    val composite = LTSACall.doCompile(spec, "T").doCompose()
    val sm = StateMachine(composite)
    return bfs(sm, trace)
  }

  /**
   * Using BFS to search for the shortest trace in the deviation model which matches the representative trace.
   */
  private fun bfs(sm: StateMachine, trace: List<String>): List<String>? {
    val q: Queue<Node> = LinkedList()
    val visited = mutableSetOf<Int>()
    val outTrans = sm.transitions.outTrans()
    var matched = false

    q.offer(Node(0, "", null))
    while (q.isNotEmpty()) {
      val n = q.poll()
      if (n.s in visited)
        continue
      val p = mutableListOf<String>()
      var nn: Node? = n
      while (nn != null) {
        if (nn.pre != null && (isEnvEvent(nn.t) || isErrEvent(nn.t)))
          p.add(0, nn.t)
        nn = nn.pre
      }
      if (n.s == -1) {
        return p
      } else {
        visited.add(n.s)
        matched = matched || p.filter { it in waGenerator.alphabetOfWA() } == trace.subList(0, trace.size - 1)
        for (t in outTrans[n.s] ?: emptyList()) {
          if (t.third in visited)
            continue
          if (matched)
            q.offer(Node(t.third, sm.alphabet[t.second], n))
          else if (!isErrEvent(sm.alphabet[t.second]))
            q.offer(Node(t.third, sm.alphabet[t.second], n))
        }
      }
    }
    return null
  }

}
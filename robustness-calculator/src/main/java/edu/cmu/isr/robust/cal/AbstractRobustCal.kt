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
import edu.cmu.isr.robust.util.Trace
import edu.cmu.isr.robust.wa.AbstractWAGenerator
import edu.cmu.isr.robust.wa.Corina02
import java.util.*

interface RobustCal {
  /**
   * The name of the process of the generated weakest assumption FSP model
   */
  var nameOfWA: String

  /**
   * Return the FSP spec of the weakest assumption
   */
  fun getWA(sink: Boolean = false): String

  /**
   * Compute the set of traces allowed by the system but would violate the safety property.
   */
  fun computeUnsafeBeh(): List<Trace>

  /**
   * The entrance function to compute the robustness. It first generates the weakest assumption, and then build the
   * representation model and compute the representative traces, and finally, match an explanation for each
   * representative trace respectively.
   */
  fun computeRobustness(level: Int = -1, waOnly: Boolean = false, sink: Boolean = false): List<Pair<Trace, Trace?>>

  /**
   * The entrance function to compare the robustness of this model to another model, i.e., X = \Delta_This - \Delta_2.
   * @param wa2 the FSP spec of the weakest assumption of the other model
   * @param name2 the name of the process of the weakest assumption.
   */
  fun robustnessComparedTo(wa2: String, name2: String, level: Int = -1, sink: Boolean = false): List<Trace>
}

abstract class AbstractRobustCal(val sys: String, val env: String, val p: String,
                                 val verbose: Boolean = true) : RobustCal {

  /**
   * A helper class used in BFS search.
   * @param s the current state of the state machine
   * @param t the action leads to this state
   * @param pre previous node in the search
   */
  private class Node(val s: Int, val t: String, val pre: Node?)

  override var nameOfWA = "WA"
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

  override fun getWA(sink: Boolean): String {
    return wa ?: genWeakestAssumption(sink)
  }

  private fun genWeakestAssumption(sink: Boolean): String {
    // Check that SYS||ENV |= P. Our tool assumes that the system should already satisfy the property under the
    // original environment model. Thus, we check this assumption here first.
    val spec = combineSpecs(sys, env, p, "||T = (SYS || ENV || P).")
    val errs = LTSACall.doCompile(spec, "T").doCompose().propertyCheck()
    if (errs != null) {
      println("ERROR: SYS || ENV |= P does not hold, property violation or deadlock:\n\t${errs.joinToString("\n\t")}\n")
    }

    println("Alphabet for weakest assumption: ${waGenerator.alphabetOfWA()}")
    println("Generating the weakest assumption...")
    wa = waGenerator.weakestAssumption(nameOfWA, sink)
    if (verbose) {
      println("Generated Weakest Assumption:")
      println(wa)
    }
    return wa!!
  }


  override fun computeUnsafeBeh(): List<Trace> {
    val corina02 = waGenerator as? Corina02 ?: error("This function is only supported by the Corina02 approach")
    val traces = corina02.computeUnsafeBeh()
    printRepTraces(traces)
    return traces.values.flatten()
  }

  override fun computeRobustness(level: Int, waOnly: Boolean, sink: Boolean): List<Pair<Trace, Trace?>> {
    if (wa == null)
      genWeakestAssumption(sink)

    val traces = if (level == -1) {
      println("Generating the representation traces by equivalence classes...")
      waGenerator.shortestDeltaTraces(wa!!, nameOfWA)
    } else {
      println("Generating the level $level representation traces by equivalence classes...")
      waGenerator.deltaTraces(wa!!, nameOfWA, level = level)
    }

    if (waOnly) {
      println("Skipped...")
      return traces.values.flatten().map { Pair<Trace, Trace?>(it, null) }
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
  private fun printRepTraces(traces: Map<AbstractWAGenerator.EquivClass, List<Trace>>) {
    println("Number of equivalence classes: ${traces.size}")
    traces.values.flatten().forEachIndexed { i, v ->
      println("Representation Trace No.$i: $v")
    }
    println()
  }

  /**
   * Helper function to print all the <representative trace, explanation> pair.
   */
  private fun printExplanation(r: List<Pair<Trace, Trace?>>) {
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

  override fun robustnessComparedTo(wa2: String, name2: String, level: Int, sink: Boolean): List<Trace> {
    if (wa == null)
      genWeakestAssumption(sink)

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
  protected abstract fun genErrEnvironment(t: Trace): String

  /**
   * Returns True when the given action is an environmental action.
   */
  protected abstract fun isEnvEvent(a: String): Boolean

  /**
   * Returns True when the given action is an error/deviation action.
   */
  protected abstract fun isErrEvent(a: String): Boolean

  /**
   * The entrance function to match the representative trace to the shortest explanation in the deviation model.
   */
  private fun matchMinimalErr(trace: Trace): Trace? {
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
  private fun bfs(sm: StateMachine, trace: Trace): Trace? {
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
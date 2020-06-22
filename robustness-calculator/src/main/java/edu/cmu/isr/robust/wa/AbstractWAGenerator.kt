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

package edu.cmu.isr.robust.wa

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.combineSpecs
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.ltsa.propertyCheck
import edu.cmu.isr.robust.util.StateMachine

abstract class AbstractWAGenerator(val sys: String, val env: String, val p: String) {

  /**
   * A helper class used in performing the DFS search.
   * @param s is the current state
   * @param a is the action to this state
   * @param pre is the previous node
   */
  private class Node(val s: Int, val a: String, val pre: Node?)

  data class EquivClass(val s: Int, val a: String)

  /**
   * This function returns a FSP spec indicating the weakest assumption of the system against the given
   * environment and property.
   * @param name is the name of the process in the FSP spec.
   */
  abstract fun weakestAssumption(name: String): String

  /**
   * Return the alphabet of the weakest assumption.
   */
  abstract fun alphabetOfWA(): Iterable<String>

  /**
   * This function builds the representation model by computing WA |= Env. In other words, we let the original
   * environment as a safety property, thus any error trace indicates a prefix of the behaviors that are not
   * in the original environment.
   * @param wa the FSP spec of the weakest assumption
   * @param name the name of the process of the weakest assumption in the FSP spec.
   */
  private fun computeDelta(wa: String, name: String): StateMachine {
    val pEnv = projectedEnv()
    val deltaSpec = combineSpecs(pEnv, "property ||PENV = (ENV).", wa, "||D = (PENV || $name).")
    val composite = LTSACall().doCompile(deltaSpec, "D").doCompose()
    return StateMachine(composite)
  }

  /**
   * This function builds the representation model to compute the difference of two weakest assumptions by computing
   * WA1 |= WA2.
   * @param wa1 the FSP spec of the weakest assumption for system 1
   * @param name1 the name of the process of the weakest assumption in the FSP spec for system 1
   * @param wa2 the FSP spec of the weakest assumption for system 2
   * @param name2 the name of the process of the weakest assumption in the FSP spec for system 2
   */
  private fun computeX(wa1: String, name1: String, wa2: String, name2: String): StateMachine {
    val pEnv = projectedEnv()
    val checkWA2 = combineSpecs(pEnv, wa2, "property ||P_$name2 = ($name2).", "||T = (ENV || P_$name2).")
    if (LTSACall().doCompile(checkWA2, "T").doCompose().propertyCheck() != null) {
      error("Compute X only works when the weakest assumption 2 covers all the behaviors of the original environment")
    }

    val deltaSpec = combineSpecs(wa1, wa2, "property ||P_$name2 = ($name2).", "||X = ($name1 || P_$name2).")
    val composite = LTSACall().doCompile(deltaSpec, "X").doCompose()
    return StateMachine(composite)
  }

  /**
   * This function computes the representative traces of the robustness \Delta(M,E,P). It returns a map where the
   * key is an equivalence class and the value is a list of traces in that equivalence class.
   * @param level indicates the depths we should search when encountering a loop in the state machine when performing
   * the DFS search
   * @param wa the FSP spec of the weakest assumption
   * @param name the name of the process of the weakest assumption in the FSP spec.
   */
  @Deprecated("In the current design, we only search for the shortest trace.")
  fun deltaTraces(wa: String, name: String, level: Int = 0): Map<EquivClass, List<List<String>>> {
    val sm = computeDelta(wa, name)
    return deltaTraces(sm, level)
  }

  /**
   * This function computes the representative traces of the difference of two robustness
   * X = \Delta(M1,E,P) - \Delta(M2,E,P). It returns a map where the key is an equivalence class and the value is a
   * list of traces in that equivalence class.
   * @param level indicates the depths we should search when encountering a loop in the state machine when performing
   * the DFS search
   * @param wa1 the FSP spec of the weakest assumption for system 1
   * @param name1 the name of the process of the weakest assumption in the FSP spec for system 1
   * @param wa2 the FSP spec of the weakest assumption for system 2
   * @param name2 the name of the process of the weakest assumption in the FSP spec for system 2
   */
  @Deprecated("In the current design, we only search for the shortest trace.")
  fun deltaTraces(wa1: String, name1: String, wa2: String, name2: String, level: Int = 0): Map<EquivClass, List<List<String>>> {
    val sm = computeX(wa1, name1, wa2, name2)
    return deltaTraces(sm, level)
  }

  /**
   * This function computes the representative traces by given a representative model. This is the actual function
   * invoked by the public interface deltaTraces(WA, Name, Level).
   */
  private fun deltaTraces(sm: StateMachine, level: Int): Map<EquivClass, List<List<String>>> {
    if (!sm.hasError())
      return emptyMap()

    val traces = mutableMapOf<EquivClass, MutableList<List<String>>>()
    val dfs = buildDFS(sm, level, traces)
    dfs(Node(0, "", null), mapOf(0 to 1))
    return traces
  }

  /**
   * The DFS process used to search for representative traces with level x.
   */
  private fun buildDFS(sm: StateMachine, level: Int,
                       traces: MutableMap<EquivClass, MutableList<List<String>>>): (Node, Map<Int, Int>) -> Unit
  {
    val outTrans = sm.transitions.outTrans()

    fun dfs(n: Node, visited: Map<Int, Int>) {
      if (n.s == -1) {
        var nn = n
        val trace = mutableListOf<String>()
        while (nn.pre != null) {
          trace.add(0, nn.a)
          nn = nn.pre!!
        }
        val c = EquivClass(n.pre!!.s, n.a)
        traces[c] = traces[c] ?: mutableListOf()
        traces[c]!!.add(trace)
        return
      }
      for (t in outTrans[n.s] ?: emptyList()) {
        if (t.third in visited) {
          if (visited[t.third]!! <= level) {
            val newVisited = visited.toMutableMap()
            newVisited[t.third] = visited[t.third]!! + 1
            dfs(Node(t.third, sm.alphabet[t.second], n), newVisited)
          }
        } else {
          val newVisited = visited.toMutableMap()
          newVisited[t.third] = 1
          dfs(Node(t.third, sm.alphabet[t.second], n), newVisited)
        }
      }
    }

    return ::dfs
  }

  /**
   * This function computes the shortest representative traces of the robustness \Delta(M,E,P). It returns a map
   * where the key is an equivalence class and the value is a list of traces in that equivalence class.
   * @param wa the FSP spec of the weakest assumption
   * @param name the name of the process of the weakest assumption in the FSP spec.
   */
  fun shortestDeltaTraces(wa: String, name: String): Map<EquivClass, List<List<String>>> {
    val sm = computeDelta(wa, name)
    return shortestDeltaTraces(sm)
  }

  /**
   * This function computes the shortest representative traces of the difference of two robustness
   * X = \Delta(M1,E,P) - \Delta(M2,E,P). It returns a map where the key is an equivalence class and the value
   * is a list of traces in that equivalence class.
   * @param wa1 the FSP spec of the weakest assumption for system 1
   * @param name1 the name of the process of the weakest assumption in the FSP spec for system 1
   * @param wa2 the FSP spec of the weakest assumption for system 2
   * @param name2 the name of the process of the weakest assumption in the FSP spec for system 2
   */
  fun shortestDeltaTraces(wa1: String, name1: String, wa2: String, name2: String): Map<EquivClass, List<List<String>>> {
    val sm = computeX(wa1, name1, wa2, name2)
    return shortestDeltaTraces(sm)
  }

  /**
   * This function computes the shortest representative traces of a given representation model.
   * For a representation model, S_err \subseteq S is the set of states which has transitions to the error state,
   * i.e., S_err = { s | \forall s:S . \exists a:A . (s, a, error) \in R }.
   * Then, we compute the shortest traces from the initial state s_0 to each state in S_err.
   */
  private fun shortestDeltaTraces(sm: StateMachine): Map<EquivClass, List<List<String>>> {
    if (!sm.hasError())
      return emptyMap()

    val traces = mutableMapOf<EquivClass, List<List<String>>>()
    val transToErr = sm.transitions.inTrans()[-1] ?: emptyList()
    val paths = sm.pathFromInit(transToErr.map { it.first }.toSet())
    for (t in transToErr) {
      val a = sm.alphabet[t.second]
      traces[EquivClass(t.first, a)] = listOf((paths[t.first] ?: error(t.first)) + a)
    }
    return traces
  }

  /**
   * This is a helper function which project the environment to the alphabet of the weakest assumption and perform
   * tau elimination and subset construction.
   */
  private fun projectedEnv(): String {
    // For the environment, expose only the alphabets in the weakest assumption, and do tau elimination
    val pEnv = combineSpecs(env, "||E = (ENV)@{${alphabetOfWA().joinToString(", ")}}.")
    val composite = LTSACall().doCompile(pEnv, "E").doCompose()
    val envSM = StateMachine(composite).tauElmAndSubsetConstr().first
    return envSM.buildFSP("ENV")
  }
}
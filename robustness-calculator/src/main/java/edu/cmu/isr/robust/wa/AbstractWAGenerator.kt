package edu.cmu.isr.robust.wa

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.combineSpecs
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.ltsa.propertyCheck
import edu.cmu.isr.robust.util.StateMachine

abstract class AbstractWAGenerator(val sys: String, val env: String, val p: String) {

  private class Node(val s: Int, val a: String, val pre: Node?)

  /**
   *
   */
  abstract fun weakestAssumption(name: String): String

  abstract fun alphabetOfWA(): Iterable<String>

  private fun computeDelta(wa: String, name: String): StateMachine {
    val pEnv = projectedEnv()
    val deltaSpec = combineSpecs(pEnv, "property ||PENV = (ENV).", wa, "||D = (PENV || $name).")
    val composite = LTSACall().doCompile(deltaSpec, "D").doCompose()
    return StateMachine(composite)
  }

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

  fun deltaTraces(wa: String, name: String, level: Int = 0): List<List<String>> {
    val sm = computeDelta(wa, name)
    if (!sm.hasError())
      return emptyList()

    val traces = mutableListOf<List<String>>()
    val dfs = buildDFS(sm, level, traces)
    dfs(Node(0, "", null), mapOf(0 to 1))
    return traces
  }

  fun deltaTraces(wa1: String, name1: String, wa2: String, name2: String, level: Int = 0): List<List<String>> {
    val sm = computeX(wa1, name1, wa2, name2)
    if (!sm.hasError())
      return emptyList()

    val traces = mutableListOf<List<String>>()
    val dfs = buildDFS(sm, level, traces)
    dfs(Node(0, "", null), mapOf(0 to 1))
    return traces
  }

  private fun buildDFS(sm: StateMachine, level: Int, traces: MutableList<List<String>>): (Node, Map<Int, Int>) -> Unit {
    val outTrans = sm.transitions.outTrans()

    fun dfs(n: Node, visited: Map<Int, Int>) {
      if (n.s == -1) {
        var nn = n
        val trace = mutableListOf<String>()
        while (nn.pre != null) {
          trace.add(0, nn.a)
          nn = nn.pre!!
        }
        traces.add(trace)
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
   *
   */
  fun shortestDeltaTraces(wa: String, name: String): List<List<String>> {
    val sm = computeDelta(wa, name)
    if (!sm.hasError())
      return emptyList()

    val traces = mutableListOf<List<String>>()
    val transToErr = sm.transitions.inTrans()[-1] ?: emptyList()
    val paths = sm.pathFromInit(transToErr.map { it.first }.toSet())
    for (t in transToErr) {
      traces.add((paths[t.first] ?: error(t.first)) + sm.alphabet[t.second])
    }
    return traces
  }

  fun shortestDeltaTraces(wa1: String, name1: String, wa2: String, name2: String): List<List<String>> {
    val sm = computeX(wa1, name1, wa2, name2)
    if (!sm.hasError())
      return emptyList()

    val traces = mutableListOf<List<String>>()
    val transToErr = sm.transitions.inTrans()[-1] ?: emptyList()
    val paths = sm.pathFromInit(transToErr.map { it.first }.toSet())
    for (t in transToErr) {
      traces.add((paths[t.first] ?: error(t.first)) + sm.alphabet[t.second])
    }
    return traces
  }

  /**
   *
   */
  private fun projectedEnv(): String {
    // For the environment, expose only the alphabets in the weakest assumption, and do tau elimination
    val pEnv = combineSpecs(env, "||E = (ENV)@{${alphabetOfWA().joinToString(", ")}}.")
    val composite = LTSACall().doCompile(pEnv, "E").doCompose()
    val envSM = StateMachine(composite).tauElmAndSubsetConstr().first
    return envSM.buildFSP("ENV")
  }
}
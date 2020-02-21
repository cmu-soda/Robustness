package edu.cmu.isr.robust.wa

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.combineSpecs
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Transition

abstract class AbstractWAGenerator(val sys: String, val env: String, val p: String) {

  private class Node(val s: Int, val a: String, val pre: Node?)

  /*private abstract class Expr
  private class Empty : Expr()
  private class Literal(val v: String) : Expr()
  private class Concact(val a: Expr, val b: Expr) : Expr()
  private class Star(val a: Expr) : Expr()
  private class Union(val exprs: List<Expr>) : Expr()
  private infix fun Expr?.union(b: Expr?): Expr? {
    if (this != null && b != null) {
      if (this is Union && b is Union) {
        return Union(this.exprs + b.exprs)
      } else if (this is Union) {
        return Union(this.exprs + b)
      } else if (b is Union) {
        return Union(b.exprs + this)
      } else {
        return Union(listOf(this, b))
      }
    }
    return this ?: b
  }*/

  /**
   *
   */
  abstract fun weakestAssumption(name: String = "WA"): String

  abstract fun alphabetOfWA(): Iterable<String>

  fun computeDelta(wa: String, name: String = "WA"): StateMachine {
    val pEnv = projectedEnv()
    val deltaSpec = combineSpecs(pEnv, "property ||PENV = (ENV).", wa, "||D_$name = (PENV || $name).")
    val composite = LTSACall().doCompile(deltaSpec, "D_$name").doCompose()
    return StateMachine(composite)
  }

  fun deltaTraces(wa: String, name: String = "WA", level: Int = 0): List<List<String>> {
    val sm = computeDelta(wa, name)
    if (!sm.hasError())
      return emptyList()

    val outTrans = sm.transitions.outTrans()
    val traces = mutableListOf<List<String>>()
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
        val newVisited = visited.toMutableMap()
        if (t.third in visited) {
          if (visited[t.third]!! <= level) {
            newVisited[t.third] = visited[t.third]!! + 1
            dfs(Node(t.third, sm.alphabet[t.second], n), newVisited)
          }
        } else {
          newVisited[t.third] = 1
          dfs(Node(t.third, sm.alphabet[t.second], n), newVisited)
        }
      }
    }

    dfs(Node(0, "", null), mapOf(0 to 1))
    return traces
  }


  /**
   *
   */
  fun shortestDeltaTraces(wa: String, name: String = "WA"): List<List<String>> {
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
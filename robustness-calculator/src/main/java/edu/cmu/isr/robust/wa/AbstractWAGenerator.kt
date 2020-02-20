package edu.cmu.isr.robust.wa

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.combineSpecs
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Transition

abstract class AbstractWAGenerator(val sys: String, val env: String, val p: String) {

  private abstract class Expr
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
  }

  /**
   *
   */
  abstract fun weakestAssumption(name: String = "WA"): String

  abstract fun alphabetOfWA(): Iterable<String>

  fun deltaTraces(wa: String, name: String = "WA"): StateMachine {
    val pEnv = projectedEnv()
    val deltaSpec = combineSpecs(pEnv, "property ||PENV = (ENV).", wa, "||D_$name = (PENV || $name).")
    val composite = LTSACall().doCompile(deltaSpec, "D_$name").doCompose()
    return StateMachine(composite)
  }

  /**
   * @requires The state machine of the detal trace should not have tau/epsilon transition.
   * See: https://cs.stackexchange.com/questions/2016/how-to-convert-finite-automata-to-regular-expressions
   */
  /*fun deltaTracesInRegex(wa: String, name: String = "WA"): String {
    val sm = deltaTraces(wa, name)
    if (!sm.hasError())
      return ""
    val m = sm.maxIndexOfState()
    val A = Array(m+2) { arrayOfNulls<String>(m+2) }
    val B = arrayOfNulls<String>(m+2)

    fun hasTransition(i: Int, a: Int, j: Int): Boolean {
      val s = if (i != m + 1) i else -1
      val s_ = if (j != m + 1) j else -1
      return sm.transitions.outTrans()[s]?.find { it.second == a && it.third == s_ } != null
    }

    infix fun String?.concat(b: String?): String? {
      return if (this == null)
        null
      else if (b == null)
        this
      else
        this + b
    }

    fun star(a: String?): String? {
      return when (a) {
        null -> null
        "" -> ""
        else -> "($a)*"
      }
    }

    infix fun String?.union(b: String?): String? {
      if (this != null && b != null) {
        return if (this == "" && b == "")
          ""
        else if (this == "")
          "$b?"
        else if (b == "")
          "$this?"
        else if (this == b)
          this
        else
          "($this+$b)"
      }

      return this ?: b
    }

    B[m+1] = "" // -1 is final state
    for (i in 0..m+1) {
      for (j in 0..m+1) {
        for (a in sm.alphabet.indices) {
          if (hasTransition(i, a, j)) {
            A[i][j] = sm.alphabet[a]
          }
        }
      }
    }
    for (n in m+1 downTo 0) {
      if (A[n][n] != null) {
        B[n] = star(A[n][n]) concat B[n]
        for (j in 0..n)
          A[n][j] = star(A[n][n]) concat A[n][j]
      }
      for (i in 0..n) {
        if (A[i][n] != null) {
          B[i] = B[i] union (A[i][n] concat B[n])
          for (j in 0..n) {
            A[i][j] = A[i][j] union (A[i][n] concat A[n][j])
          }
        }
      }
    }
    return B[0]!!
  }*/

  fun deltaTracesInRegex(wa: String, name: String = "WA"): String {
    val sm = deltaTraces(wa, name)
    if (!sm.hasError())
      return ""

    val outTrans = sm.transitions.outTrans()
    var result: Expr? = null
    fun dfs(s: Int, expr: Expr, path: List<Transition>, visited: Set<Int>) {
      if (s == -1) {
        result = result union expr
        return
      }
      val loops = outTrans[s]?.filter { it.third in visited }
      if (loops != null) {
        for (loop in loops) {

        }
      }
    }
  }

  /**
   *
   */
  fun shortestDeltaTraces(wa: String, name: String = "WA"): List<List<String>> {
    val sm = deltaTraces(wa, name)
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
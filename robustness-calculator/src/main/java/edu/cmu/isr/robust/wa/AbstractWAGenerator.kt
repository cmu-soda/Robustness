package edu.cmu.isr.robust.wa

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.combineSpecs
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.util.StateMachine

abstract class AbstractWAGenerator(val sys: String, val env: String, val p: String) {
  /**
   *
   */
  abstract fun weakestAssumption(name: String = "WA"): String

  abstract fun alphabetOfWA(): Iterable<String>

  /**
   *
   */
  fun shortestErrTraces(wa: String, name: String = "WA"): List<List<String>> {
    val pEnv = projectedEnv()
    val deltaSpec = combineSpecs(pEnv, "property ||PENV = (ENV).", wa, "||D_$name = (PENV || $name).")
    val composite = LTSACall().doCompile(deltaSpec, "D_$name").doCompose()
    val sm = StateMachine(composite)
    if (!sm.hasError()) {
      return emptyList()
    }

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
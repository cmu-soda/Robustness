package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.EOFMTranslator2
import edu.cmu.isr.ltsa.util.StateMachine

class EOFMRobustCal(
    val machine: String,
    val p: String,
    val human: EOFMS,
    val initState: Map<String, String>,
    val world: List<String>,
    val relabels: Map<String, String>
) {
  private fun genHumanModel(translator: EOFMTranslator2): String {
    // Translate human behavior model
    val actions = translator.getActions()
    val builder = StringBuilder()
    translator.translate(builder)

    // Generate concise human model
    val ltsaCall = LTSACall()
    val renamedMachine = renameConsts(machine, "M")
    val composite = ltsaCall.doCompile(
        "$builder$renamedMachine||G = (SYS || ENV)@{${actions.joinToString(", ")}}.",
        "G"
    ).doCompose()
    val conciseHuman = StateMachine(composite.composition).tauElmAndSubsetConstr().first
    return conciseHuman.minimize().buildFSP("ENV")
  }

  private fun genHumanModelErr(translator: EOFMTranslator2): String {
    val builder = StringBuilder()
    translator.translate(builder, withError = true)
    return builder.toString()
  }

  fun run() {
    val translator = EOFMTranslator2(human, initState, world, relabels)
    val humanModel = genHumanModel(translator)
    val humanModelErr = genHumanModelErr(translator)

    // Calculate weakest assumption of the system and extract deviation traces
    val cal = RobustCal(p, humanModel, machine)
    val traces = cal.deltaTraces("COFFEE")

    val renamedMachine = renameConsts(machine, "M")
    // Match each deviation trace back to the human model with error
    for (t in traces) {
      val trace = "TRACE = (${t.joinToString(" -> ")} -> ERROR)+{${cal.getAlphabet().joinToString(",")}}."
      val ltsaCall = LTSACall()
      val tComposite = ltsaCall.doCompile(
          "$humanModelErr$renamedMachine$trace||T = (SYS || ENV || TRACE).", "T"
      ).doCompose()
      val tSM = StateMachine(tComposite.composition)
      println("Match error trace: $t")
      searchErrors(tSM, t[t.size - 2])
      break
    }
  }

  private fun searchErrors(sm: StateMachine, lastNormEvent: String) {
    val visited = mutableSetOf<Int>()
    val validMap = mutableMapOf<Int, Pair<Boolean, Map<String, Int>>>()

    fun dfs(s: Int, foundLast: Boolean, t: Triple<Int, Int, Int>): Pair<Boolean, Map<String, Int>> {
      if (s in visited) {
        if (validMap[s]?.first == true) {
          return validMap[s]!!
        }
        return Pair(false, emptyMap())
      }

      visited.add(s)
      if (s == 0) {
        validMap[s] = Pair(true, emptyMap())
        return validMap[s]!!
      }
      val ts = if (foundLast) {
        sm.transitions.filter {
          val a = sm.alphabet[it.second]
          it.third == s && !a.startsWith("omission") && !a.startsWith("commission")
              && !a.startsWith("repetition")
        }
      } else {
        sm.transitions.filter { it.third == s }
      }
      val errors = mutableMapOf<String, Int>()
      var atLeastOne = false
      for (next in ts) {
        val r = dfs(next.first, foundLast || sm.alphabet[next.second] == lastNormEvent, next)
        if (r.first) {
          atLeastOne = true
          for ((k, v) in r.second) {
            errors[k] = (errors[k] ?: 0) + v
          }

          val a = sm.alphabet[next.second]
          if (a.startsWith("omission") || a.startsWith("commission") || a.startsWith("repetition")) {
            errors[a] = (errors[a] ?: 0) + 1
          }
        }
      }
      validMap[s] = if (atLeastOne) {
        Pair(true, errors)
      } else {
        Pair(false, mapOf<String, Int>())
      }
      return validMap[s]!!
    }

    val errors = mutableMapOf<String, Int>()
    for (t in sm.transitions.filter { it.third == -1 }) {
      val r = dfs(t.first, false, t)
      if (r.first) {
        for ((k, v) in r.second)
          errors[k] = (errors[k] ?: 0) + v
      }
    }

    println(errors)
  }
}
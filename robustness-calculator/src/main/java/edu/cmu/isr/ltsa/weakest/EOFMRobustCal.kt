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
      searchErrors(tSM, t.last())
      break
    }
  }

  private fun searchErrors(sm: StateMachine, last: String) {
    val transNormToErr = mutableSetOf<Triple<Int, Int, Int>>()
    val ts = sm.transitions.filter { it.third == -1 }
    val validMap = mutableMapOf<Int, Boolean>()
    val visited = mutableSetOf<Int>()

    fun dfs(s: Int, foundLast: Boolean, t: Triple<Int, Int, Int>): Boolean {
      if (s in visited) {
        if (validMap[s] == true) {
          transNormToErr.add(t)
          return true
        }
        return false
      }

      visited.add(s)
      if (foundLast && s == 0) {
        validMap[s] = true
        transNormToErr.add(t)
        return true
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
      var atLeastOne = false
      for (next in ts) {
        if (dfs(next.first, foundLast || sm.alphabet[next.second] == last, next))
          atLeastOne = true
      }
      return if (atLeastOne) {
        validMap[s] = true
        transNormToErr.add(t)
        true
      } else {
        validMap[s] = false
        false
      }
    }

    for (t in ts) {
      dfs(t.first, false, t)
    }

    println(StateMachine(transNormToErr, sm.alphabet).buildFSP())
  }
}
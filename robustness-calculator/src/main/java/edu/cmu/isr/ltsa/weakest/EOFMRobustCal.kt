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
    val visitedMap = mutableMapOf<Int, Boolean>()
    val trans = mutableSetOf<Triple<Int, Int, Int>>()

    fun dfs(s: Int, foundLast: Boolean): Boolean {
      if (s in visited) {
        return visitedMap[s]?: false
      }
      visited.add(s)
      if (s == 0) {
        visitedMap[s] = true
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
      for (t in ts) {
        val isLast = sm.alphabet[t.second] == lastNormEvent
        val r = dfs(t.first, foundLast || isLast)
        if (r) {
          atLeastOne = true
          if (!foundLast)
            trans.add(if (isLast) t.copy(first = 0) else t)
        }
      }
      visitedMap[s] = atLeastOne
      return atLeastOne
    }

    dfs(-1, false)
    println(StateMachine(trans, sm.alphabet).buildFSP())
  }
}
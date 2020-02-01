package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.combineSpecs
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.EOFMTranslator2
import edu.cmu.isr.ltsa.util.SimpleTransitions
import edu.cmu.isr.ltsa.util.StateMachine
import edu.cmu.isr.ltsa.util.Transition

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
    val spec = combineSpecs(builder.toString(), machine, "||G = (SYS || ENV)@{${actions.joinToString(", ")}}.")
    val composite = ltsaCall.doCompile(spec, "G").doCompose()
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

    // Match each deviation trace back to the human model with error
    for (t in traces) {
      val trace = "TRACE = (${t.joinToString(" -> ")} -> ERROR)+{${cal.getAlphabet().joinToString(",")}}."
      val ltsaCall = LTSACall()
      val spec = combineSpecs(humanModelErr, machine, trace, "||T = (SYS || ENV || TRACE).")
      val tComposite = ltsaCall.doCompile(spec, "T").doCompose()
      val tSM = StateMachine(tComposite.composition)
      println("Match error trace: $t")
      searchErrors(tSM, t[t.size - 2])
      break
    }
  }

  private fun searchErrors(sm: StateMachine, lastNormEvent: String) {
    val visited = mutableSetOf<Int>()
    val visitedMap = mutableMapOf<Int, Boolean>()
    val trans = mutableSetOf<Transition>()

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
        sm.transitions.inTrans()[s]?.filter {
          val a = sm.alphabet[it.second]
          !a.startsWith("omission") && !a.startsWith("commission")
              && !a.startsWith("repetition")
        }
      } else {
        sm.transitions.inTrans()[s]
      }
      var atLeastOne = false
      for (t in ts ?: emptyList()) {
        val isLast = sm.alphabet[t.second] == lastNormEvent
        val r = dfs(t.first, foundLast || isLast)
        if (r) {
          atLeastOne = true
          if (!foundLast && s != -1) {
            val a = sm.alphabet[t.second]
            val notError = !a.startsWith("omission") && !a.startsWith("commission") &&
                !a.startsWith("repetition")
            trans.add(if (isLast) t.copy(first = 0) else if (notError) t.copy(second = sm.tau) else t)
          }
        }
      }
      visitedMap[s] = atLeastOne
      return atLeastOne
    }

    dfs(-1, false)
    println(StateMachine(SimpleTransitions(trans), sm.alphabet).minimize().buildFSP())
  }
}
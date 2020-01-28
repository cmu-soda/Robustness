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
    val world: Map<String, List<Triple<String, String, String>>>,
    val relabels: Map<String, String>
) {
  fun run() {
    // Translate human behavior model
    val translator = EOFMTranslator2(human, initState, world, relabels)
    val actions = translator.getActions().map { if (it in relabels) relabels[it]!! else it }
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
    val conciseHumanSpec = conciseHuman.minimize().buildFSP("ENV")

    // Calculate weakest assumption of the system and extract deviation traces
    val cal = RobustCal(p, conciseHumanSpec, machine)
    val traces = cal.deltaTraces("COFFEE")

    // Translate human behavior model with error
    val translatorErr = EOFMTranslator2(human, initState, world, relabels, withError = true)
    val builderErr = StringBuilder()
    translatorErr.translate(builderErr)
    // Match each deviation trace back to the human model with error
    for (t in traces) {
      val tComposite = ltsaCall.doCompile(
          "$builderErr$renamedMachine$t||T = (SYS || ENV || TRACE).", "T"
      ).doCompose()
      val tSM = StateMachine(tComposite.composition)
      val paths = tSM.pathToInit()
      var minTrace: List<Triple<Int, Int, Int>>? = null
      val transToErr = tSM.transitions.filter { it.third == -1 }
      for (trans in transToErr) {
        val tToErr = (paths[trans.first]?: error("No path to init for '${trans.first}'")) + trans
        if (minTrace == null || tToErr.size < minTrace.size)
          minTrace = tToErr
      }
      println("Orignial: $t")
      println("FOUND: TRACE = (\n${minTrace?.joinToString(" -> \n") { tSM.alphabet[it.second] }} -> END).\n")
    }
  }
}
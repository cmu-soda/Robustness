package edu.cmu.isr.robust.cal

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.combineSpecs
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.eofm.Action
import edu.cmu.isr.robust.eofm.Activity
import edu.cmu.isr.robust.eofm.EOFMS
import edu.cmu.isr.robust.eofm.EOFMTranslator2
import edu.cmu.isr.robust.ltsa.buildTrace
import edu.cmu.isr.robust.util.StateMachine

class EOFMRobustCal private constructor(
    sys: String,
    p: String,
    private val rawHumanModel: String,
    private val conciseHumanModel: String,
    private val translator: EOFMTranslator2
) : AbstractRobustCal(sys, conciseHumanModel, p) {

  companion object {
    @JvmStatic
    fun create(sys: String, p: String, human: EOFMS, initState: Map<String, String>,
               world: List<String>, relabels: Map<String, String>): EOFMRobustCal
    {
      println("Generating the LTSA spec of the EOFM human model...")
      val translator = EOFMTranslator2(human, initState, world, relabels)
      val rawModel = genHumanModel(translator)
      val conciseModel = genConciseHumanModel(translator, sys, rawModel)
      println(conciseModel)
      return EOFMRobustCal(sys, p, rawModel, conciseModel, translator)
    }

    /**
     *
     */
    @JvmStatic
    private fun genHumanModel(translator: EOFMTranslator2): String {
      val builder = StringBuilder()
      translator.translate(builder)
      return builder.toString()
    }

    /**
     *
     */
    @JvmStatic
    private fun genConciseHumanModel(translator: EOFMTranslator2, sys: String, rawModel: String): String {
      val actions = translator.getActions()

      val rawComposite = LTSACall().doCompile(rawModel, "ENV").doCompose()
      println("Translated EOFM LTS: ${rawComposite.composition.maxStates} states and ${rawComposite.composition.ntransitions()} transitions.")

      val spec = combineSpecs(rawModel, sys, "||G = (SYS || ENV)@{${actions.joinToString(", ")}}.")
      val composite = LTSACall().doCompile(spec, "G").doCompose()
      val conciseHuman = StateMachine(composite).tauElmAndSubsetConstr().first
      return conciseHuman.minimize().buildFSP("ENV")
    }

    /**
     *
     */
    @JvmStatic
    private fun genHumanErrModel(translator: EOFMTranslator2): String {
      val builder = StringBuilder()
      translator.translate(builder, withError = true)
      return builder.toString()
    }

    /**
     *
     */
    @JvmStatic
    private fun genHumanErrModel(translator: EOFMTranslator2, errs: List<String>): String {
      val builder = StringBuilder()
      translator.translate(builder, errs = errs)
      return builder.toString()
    }
  }

  /**
   *
   */
  /*fun errsNotRobustAgainst(vararg errType: String) {
    println("Searching an trace that contains errors '${errType.joinToString(", ")}'...")
    // Build constraint process
    val humanErrModel = genHumanErrModel(errType.asList())
    val spec = combineSpecs(humanErrModel, waSinked, "property ||PWE = (WE).", "||T = (ENV || PWE).")
    val composite = LTSACall().doCompile(spec, "T").doCompose()
    val sm = StateMachine(composite.composition)
    if (!sm.hasError()) {
      println("System is robust against errors '${errType.joinToString(", ")}'")
      return
    }

    fun bfs(): List<String>? {
      val q: Queue<Node> = LinkedList()
      val visited = mutableSetOf<Int>()
      val outTrans = sm.transitions.outTrans()

      q.offer(Node(0, "", null))
      while (q.isNotEmpty()) {
        val n = q.poll()
        if (n.s in visited)
          continue
        if (n.s == -1) {
          val errs = errType.map { it to false }.toMap().toMutableMap()
          val p = mutableListOf<String>()
          var nn: Node? = n
          while (nn != null) {
            if (nn.pren != null)
              p.add(0, nn.t)
            if (nn.t in errs)
              errs[nn.t] = true
            nn = nn.pren
          }
          if (errs.values.reduce { acc, b -> acc && b })
            return p
        } else {
          visited.add(n.s)
          for (t in outTrans[n.s] ?: emptyList()) {
            if (t.third !in visited)
              q.offer(Node(t.third, sm.alphabet[t.second], n))
          }
        }
      }
      return null
    }

    val t = bfs()

//    val traces = mutableListOf<List<String>>()
//    val transToErr = sm.transitions.inTrans()[-1] ?: emptyList()
//    val paths = sm.pathFromInit(transToErr.map { it.first }.toSet())
//    for (t in transToErr) {
//      traces.add((paths[t.first] ?: error(t.first)) + sm.alphabet[t.second])
//    }
//    val t = traces.filter { it.containsAll(errType.asList()) }.minBy { it.size }

    if (t != null) {
      val ft = t.filter { isAction(it) || isHumanError(it) }
      println("Found a trace to property violation with '${errType.joinToString(", ")}':")
      println("\t${ft.joinToString("\n\t")}\n")
    } else
      println("ERROR: Cannot find an error trace.\n")
  }*/

  override fun genErrEnvironment(t: List<String>): String {
    // Match the normative prefix with the human model to get activity states
    val normPrefix = t.subList(0, t.size - 1)
    val activityStates = matchNormTrace(normPrefix).filter { !isEnvEvent(it) }
    val path = translator.pathToAction(t.last())
    val errs = matchErrors(path.reversed(), activityStates)
    println("Potential errors: $errs")
    return genHumanErrModel(translator, errs.toList())
  }

  override fun isEnvEvent(a: String): Boolean {
    return !isErrEvent(a) && !a.startsWith("start") && !a.startsWith("end") &&
        !a.startsWith("repeat") && !a.startsWith("reset") && !a.startsWith("skip")
  }

  override fun isErrEvent(a: String): Boolean {
    return a.startsWith("omission") || a.startsWith("commission") || a.startsWith("repetition")
  }

  private fun matchNormTrace(t: List<String>): List<String> {
    val tSpec = buildTrace(t, waGenerator.alphabetOfWA())
    val spec = combineSpecs(rawHumanModel, sys, tSpec, "||T = (SYS || ENV || TRACE).")
    val composite = LTSACall().doCompile(spec, "T").doCompose()
    val sm = StateMachine(composite)
    return sm.pathFromInit(-1)
  }

  /**
   * @ensures path is from the last activity directly connected to the action to the root activity.
   */
  private fun matchErrors(path: List<Activity>, states: List<String>): Set<String> {
    val errs = mutableSetOf<String>()
    var i = 0
    var substates = states
    while (i < path.size) {
      val a = path[i]
      val s = lastStateOfActivity(substates, a)
      if (s == null || s.startsWith("end_")) { // the activity has not started or ended
        errs.add("commission_${translator.translatedName(a)}")
      } else { // the activity has started or repeated
        errs.add("repetition_${translator.translatedName(a)}")
        break
      }
      i++
    }
    i = if (i == path.size) i - 1 else i
    while (i > 0) {
      val a = path[i]
      val idx = lastStateIndexOfActivity(substates, a)
      substates = substates.subList(if (idx == -1) 0 else idx, substates.size)

      val op = a.decomposition.operator
      val subs = a.decomposition.subActivities
      // If op is 'ord', then all the siblings before this activity should have already ended or have omission error.
      if (op == "ord") {
        var j = 0
        while (j < subs.size) {
          val s = lastStateOfActivity(substates, subs[j] as Activity)
          if (s != null && (s.startsWith("start_") || s.startsWith("repeat_")))
            break
          j++
        }
        var k = subs.indexOf(path[i-1])
        k = if (j > k) k + subs.size else k
        while (j < k) {
          val sub = subs[j % subs.size] as Activity
          val s = lastStateOfActivity(substates, sub)
          errs.add("omission_${translator.translatedName(sub)}")
          if (s != null && (s.startsWith("start_") || s.startsWith("repeat_"))) {
            errs.addAll(matchOmissions(sub, substates.subList(substates.indexOf(s), substates.size)))
          }
          j++
        }
      } else {
        for (sub in subs) {
          val s = lastStateOfActivity(substates, sub as Activity)
          if (s != null && (s.startsWith("start_") || s.startsWith("repeat_"))) {
            errs.add("omission_${translator.translatedName(sub)}")
            errs.addAll(matchOmissions(sub, substates.subList(substates.indexOf(s), substates.size)))
          }
        }
      }
      i--
    }

    return errs
  }

  /**
   * @ensures activity should already started
   */
  private fun matchOmissions(activity: Activity, states: List<String>): Set<String> {
    val errs = mutableSetOf<String>()
    val op = activity.decomposition.operator
    val subs = activity.decomposition.subActivities
    if (op == "ord" && subs.first() is Action)
      return errs
    // if the decomposition operator for the current node is 'and' or 'ord', then all its children should either
    // already ended or have omission error.
    if (op.startsWith("and") || op == "ord") {
      for (sub in subs) {
        val s = lastStateOfActivity(states, sub as Activity)
        if (s == null)  // the sub-activity has not started
          errs.add("omission_${translator.translatedName(sub)}")
        else if (s.startsWith("start_") || s.startsWith("repeat_")) { // has started but not ended
          errs.add("omission_${translator.translatedName(sub)}")
          errs.addAll(matchOmissions(sub, states.subList(states.indexOf(s), states.size)))
        }
      }
    }

    return errs
  }

  /**
   *
   */
  private fun lastStateOfActivity(states: List<String>, activity: Activity): String? {
    return states.findLast { it.endsWith(translator.translatedName(activity)) }
  }

  /**
   *
   */
  private fun lastStateIndexOfActivity(states: List<String>, activity: Activity): Int {
    return states.indexOfLast { it.endsWith(translator.translatedName(activity)) }
  }

}
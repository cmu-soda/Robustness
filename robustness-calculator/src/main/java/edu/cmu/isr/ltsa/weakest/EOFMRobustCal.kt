package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.combineSpecs
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.eofm.Action
import edu.cmu.isr.ltsa.eofm.Activity
import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.EOFMTranslator2
import edu.cmu.isr.ltsa.propertyCheck
import edu.cmu.isr.ltsa.util.StateMachine
import java.util.*

class EOFMRobustCal(
    private val machine: String,
    private val p: String,
    human: EOFMS,
    initState: Map<String, String>,
    world: List<String>,
    relabels: Map<String, String>
) {

  private class Node(val s: Int, val t: String, val pren: Node?)

  private val translator: EOFMTranslator2 = EOFMTranslator2(human, initState, world, relabels)
  private val cal: RobustCal
  private val rawHumanModel: String
  private val conciseHumanModel: String
  private val wa: String
  private val waSinked: String


  init {
    println("Generating the LTSA spec of the EOFM human model...")
    rawHumanModel = genHumanModel()
    conciseHumanModel = genConciseHumanModel()
    println(conciseHumanModel)

    // Calculate weakest assumption of the system and extract deviation traces
    println("Generating the weakest assumption...")
    cal = RobustCal(p, conciseHumanModel, machine)
    wa = cal.weakestAssumption()
    waSinked = cal.weakestAssumption(sink = true)
    println(wa)
  }

  /**
   *
   */
  private fun genHumanModel(): String {
    val builder = StringBuilder()
    translator.translate(builder)
    val translated = builder.toString()

    // Check that SYS||ENV |= P
    var spec = combineSpecs(translated, machine, p, "||T = (SYS || ENV || P).")
    val errs = LTSACall().doCompile(spec, "T").doCompose().propertyCheck()
    if (errs != null) {
      error("SYS || ENV |= P does not hold, property violation or deadlock:\n\r${errs.joinToString("\n\t")}")
    }

    return translated
  }

  /**
   *
   */
  private fun genHumanErrModel(): String {
    val builder = StringBuilder()
    translator.translate(builder, withError = true)
    return builder.toString()
  }

  private fun genHumanErrModel(errs: List<String>): String {
    val builder = StringBuilder()
    translator.translate(builder, errs = errs)
    return builder.toString()
  }

  private fun genConciseHumanModel(): String {
    val actions = translator.getActions()
    val spec = combineSpecs(rawHumanModel, machine, "||G = (SYS || ENV)@{${actions.joinToString(", ")}}.")
    val composite = LTSACall().doCompile(spec, "G").doCompose()
    val conciseHuman = StateMachine(composite.composition).tauElmAndSubsetConstr().first
    return conciseHuman.minimize().buildFSP("ENV")
  }

  /**
   *
   */
  private fun isHumanError(a: String): Boolean {
    return a.startsWith("omission") || a.startsWith("commission") || a.startsWith("repetition")
  }

  private fun isAction(a: String): Boolean {
    return !isHumanError(a) && !a.startsWith("start") && !a.startsWith("end") &&
        !a.startsWith("repeat") && !a.startsWith("reset") && !a.startsWith("skip")
  }

  /**
   *
   */
  fun errsNotRobustAgainst(vararg errType: String) {
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
  }

  /**
   *
   */
  fun errsRobustAgainst() {
    println("Generating the shorted paths to error state...")
    val traces = cal.shortestErrTraces(wa)
    if (traces.isEmpty()) {
      println("No error found. The weakest assumption to satisfy p has less behavior than the env.")
      return
    }
    for (t in traces) {
      println(t)
    }
    println()

    // Match each deviation trace back to the human model with error
    println("Matching the error trace to the erroneous human behavior model...")
    for (t in traces) {
      // Match the normative prefix with the human model to get activity states
      val normPrefix = t.subList(0, t.size - 1)
      val activityStates = matchNormTrace(normPrefix).filter { !isAction(it) }
      val path = translator.pathToAction(t.last())
      val errs = matchErros(path.reversed(), activityStates)
      println("Finding shortest human error trace to represent $t:")
      println("Potential errors: $errs")

      val humanErrModel = genHumanErrModel(errs.toList())
      val tSpec = "TRACE = (${t.joinToString(" -> ")} -> ERROR)+{${cal.getAlphabet().joinToString(",")}}."
      val spec = combineSpecs(humanErrModel, machine, tSpec, "||T = (SYS || ENV || TRACE).")
      val composite = LTSACall().doCompile(spec, "T").doCompose()
      val sm = StateMachine(composite.composition)
      shortestErrTrace(sm, t)
    }
  }

  private fun shortestErrTrace(sm: StateMachine, trace: List<String>) {

    fun bfs(): List<String>? {
      val q: Queue<Node> = LinkedList()
      val visited = mutableSetOf<Int>()
      val outTrans = sm.transitions.outTrans()
      var matched = false

      q.offer(Node(0, "", null))
      while (q.isNotEmpty()) {
        val n = q.poll()
        if (n.s in visited)
          continue
        val p = mutableListOf<String>()
        var nn: Node? = n
        while (nn != null) {
          if (nn.pren != null && (isAction(nn.t) || isHumanError(nn.t)))
            p.add(0, nn.t)
          nn = nn.pren
        }
        if (n.s == -1) {
          return p
        } else {
          visited.add(n.s)
          matched = matched || p.filter { it in cal.getAlphabet() } == trace.subList(0, trace.size - 1)
          for (t in outTrans[n.s] ?: emptyList()) {
            if (t.third in visited)
              continue
            if (matched)
              q.offer(Node(t.third, sm.alphabet[t.second], n))
            else if (!isHumanError(sm.alphabet[t.second]))
              q.offer(Node(t.third, sm.alphabet[t.second], n))
          }
        }
      }
      return null
    }

    val t = bfs()
    if (t != null) {
      println("\t${t.joinToString("\n\t")}\n")
    } else {
      println("ERROR: No trace found for $trace.\n")
    }
  }

  private fun matchNormTrace(t: List<String>): List<String> {
    val tSpec = "TRACE = (${t.joinToString(" -> ")} -> ERROR)+{${cal.getAlphabet().joinToString(",")}}."
    val spec = combineSpecs(rawHumanModel, machine, tSpec, "||T = (SYS || ENV || TRACE).")
    val composite = LTSACall().doCompile(spec, "T").doCompose()
    val sm = StateMachine(composite.composition)
    return sm.pathFromInit(-1)
  }

  /**
   * @ensures path is from the last activity directly connected to the action to the root activity.
   */
  private fun matchErros(path: List<Activity>, states: List<String>): Set<String> {
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

  private fun lastStateOfActivity(states: List<String>, activity: Activity): String? {
    return states.findLast { it.endsWith(translator.translatedName(activity)) }
  }

  private fun lastStateIndexOfActivity(states: List<String>, activity: Activity): Int {
    return states.indexOfLast { it.endsWith(translator.translatedName(activity)) }
  }

  /*
  private fun buildErrorSM(sm: StateMachine, t: List<String>): StateMachine {
    val errorSM = searchErrors(sm, if (t.size > 1) t[t.size - 2] else "")
    return removeRedundant(errorSM)
  }

  private fun buildErrorIndex(sm: StateMachine, trace: List<String>): Map<String, Int> {
    val visited = mutableSetOf<Int>()
    val visitedMap = mutableMapOf<Int, Boolean>()
    val counter = mutableMapOf<Int, Map<String, Int>>()

    fun dfs(s: Int, p: List<String>): Pair<Boolean, Map<String, Int>> {
      if (s in visited)
        return Pair(visitedMap[s] ?: false, counter[s] ?: emptyMap())
      visited.add(s)
      if (s == -1) {
        visitedMap[s] = true
        counter[s] = emptyMap()
        return Pair(visitedMap[s]!!, counter[s]!!)
      }
      val matchPrefix = p == trace.subList(0, trace.size - 1)
      val ts = if (matchPrefix) {
        sm.transitions.outTrans()[s]
      } else {
        // Find only non-error transitions if we've already found the last normal event
        sm.transitions.outTrans()[s]?.filter { !isHumanError(sm.alphabet[it.second]) }
      }
      var atLeastOne = false
      val cc = mutableMapOf<String, Int>()
      for (t in ts ?: emptyList()) {
        val a = sm.alphabet[t.second]
        val (r, c) = dfs(t.third, if (isAction(a)) p + a else p)
        if (r) {
          atLeastOne = true
          for ((k, v) in c) {
            cc[k] = (cc[k] ?: 0) + v
          }
          if (isHumanError(a))
            cc[a] = (cc[a] ?: 0) + 1
        }
      }
      visitedMap[s] = atLeastOne
      counter[s] = cc
      return Pair(atLeastOne, cc)
    }

    dfs(0, emptyList())
    return counter[0]!!
  }

  private fun searchErrors(sm: StateMachine, lastNormEvent: String): StateMachine {
    val visited = mutableSetOf<Int>()
    val visitedMap = mutableMapOf<Int, Boolean>()
    val trans = mutableSetOf<Transition>()

    fun dfs(s: Int, foundLast: Boolean): Boolean {
      if (s in visited) {
        return visitedMap[s] ?: false
      }
      visited.add(s)
      if (s == 0) {
        visitedMap[s] = true
        return true
      }
      val ts = if (foundLast) {
        // Find only non-error transitions if we've already found the last normal event
        sm.transitions.inTrans()[s]?.filter { !isHumanError(sm.alphabet[it.second]) }
      } else {
        // Ignore the repeat transition which ignores the case that human made a series of errors then decide to repeat
        // the entire activity and repeat the errors again.
        sm.transitions.inTrans()[s]?.filter { !sm.alphabet[it.second].startsWith("repeat") }
      }
      var atLeastOne = false
      for (t in ts ?: emptyList()) {
        val isLast = sm.alphabet[t.second] == lastNormEvent
        val r = dfs(t.first, foundLast || isLast)
        if (r) {
          atLeastOne = true
          if (!foundLast && s != -1) {
            trans.add(when {
              isLast -> t.copy(first = 0)
              isHumanError(sm.alphabet[t.second]) -> t
              else -> t.copy(second = sm.tau)
            })
          }
        }
      }
      visitedMap[s] = atLeastOne
      return atLeastOne
    }

    dfs(-1, false)
    return StateMachine(SimpleTransitions(trans), sm.alphabet)
  }

  private fun removeRedundant(sm: StateMachine): StateMachine {
    val endStates = sm.transitions.findEndStates()
    val (dfa, dfaStates) = sm.tauElmAndSubsetConstr()
    val redundant = dfaStates.indices.filter { (dfaStates[it] intersect endStates).isNotEmpty() }
    val trans = dfa.transitions.allTrans().filter { it.first !in redundant }
    return StateMachine(SimpleTransitions(trans), dfa.alphabet).minimize()
  }*/

}
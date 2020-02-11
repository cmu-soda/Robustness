package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.combineSpecs
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.eofm.EOFMS
import edu.cmu.isr.ltsa.eofm.EOFMTranslator2
import edu.cmu.isr.ltsa.propertyCheck
import edu.cmu.isr.ltsa.util.SimpleTransitions
import edu.cmu.isr.ltsa.util.StateMachine
import edu.cmu.isr.ltsa.util.Transition
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

    // Match each deviation trace back to the human model with error
    for (t in traces) {
      println("Matching the error trace '$t' to erroneous human behavior model...")

      val tSpec = "TRACE = (${t.subList(0, t.size-1).joinToString(" -> ")} -> ERROR)+{${cal.getAlphabet().joinToString(",")}}."
      val spec = combineSpecs(rawHumanModel, machine, tSpec, "||T = (SYS || ENV || TRACE).")
      val composite = LTSACall().doCompile(spec, "T").doCompose()
      val sm = StateMachine(composite.composition)
      println(sm.pathFromInit(-1))
      println()
    }
  }

  /*private fun shortestErrTrace(sm: StateMachine, trace: List<String>) {
    fun bfs(): List<String>? {
      val q: Queue<Triple<Int, List<String>, Set<Int>>> = LinkedList()
      val outTrans = sm.transitions.outTrans()
      q.offer(Triple(0, emptyList(), emptySet()))
      while (q.isNotEmpty()) {
        val (s, p, visited) = q.poll()
        if (s == -1)
          return p

        val prefixMatched = p.filter { it in cal.getAlphabet() } == trace.subList(0, trace.size - 1)
        for (t in outTrans[s] ?: emptyList()) {
          if (t.third in visited)
            continue
          if (prefixMatched) {
            q.offer(Triple(t.third, p + sm.alphabet[t.second], visited + s))
          }
          else if (!isHumanError(sm.alphabet[t.second])) {
            q.offer(Triple(t.third, p + sm.alphabet[t.second], visited + s))
          }
        }
      }
      return null
    }

    val t = bfs()
    val ft = t?.filter { isAction(it) || isHumanError(it) }
    if (ft != null) {
      println("Found shortest human error trace to represent $trace:")
      println("\t${ft.joinToString("\n\t")}\n")
    } else {
      println("No trace found for $trace. Potentially deadlock.\n")
    }
  }

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
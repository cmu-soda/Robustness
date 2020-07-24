package edu.cmu.isr.robust.fuzz.android

import edu.cmu.isr.robust.util.SimpleTransitions
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Transition
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.lang.IllegalStateException
import kotlin.math.max

/**
 *
 */
const val ActionDelayTime: Long = 500

/**
 *
 */
const val DumpDelayTime: Long = 1000

/**
 *
 */
class UIModelBuilder(val pkg: String, val activity: String, val steps: Int = 100, val restart: Int = 10) {

  private class UIAutomatorFail: IllegalStateException("Failed to dump the current UI layout.")

  /**
   *
   */
  private val statesMap = mutableMapOf<String, Int>()

  /**
   *
   */
  private var maxState = 0

  /**
   *
   */
  private val transitions = mutableSetOf<Transition>()

  /**
   *
   */
  private val alphabet = mutableListOf(UIStartApp, UIBackButton)

  /**
   *
   */
  private val enabled = mutableMapOf<Int, MutableMap<Int, Int>>()

  init {
    // cleanup system clipboard, this is to avoid paste text in clipboard to text boxes.
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(""), null)
  }


  /**
   *
   */
  fun build(): StateMachine {
    println("Start searching with max depth: $steps, restart: $restart...")
    for (i in 1..restart) {
      try {
        search()
      } catch (e: UIAutomatorFail) {
        println("Retry due to: ${e.message}")
        ADBHelper.killServer()
      }

      println("Number of states: ${maxState+1}")
      println("Number of transitions: ${transitions.size}")
      println("Transitions: $transitions")
      println("Alphabet: ${alphabet.map { it.toShortName() }}")
      if (isComplete()) {
        println("The search is completed in the sense that all the enabled actions are visited.")
        break
      } else {
        println("The search is not completed either because the max step is reached or the app leaves to home screen.")
        println("\n\nRetry for the $i time...")
      }
    }

    return StateMachine(SimpleTransitions(transitions), alphabet.map { it.toShortName() })
  }


  /**
   *
   */
  private fun isComplete(): Boolean {
    for ((_, e) in enabled) {
      for ((_, w) in e) {
        if (w > 0)
          return false
      }
    }
    return true
  }

  /**
   *
   */
  private fun updateEnabled(s: Int, visited: Set<Int>): Int {
    var totalW = 0
    for ((a, w) in enabled[s]?.entries!!) {
      // If the action has not been visited, keep it false
      // otherwise, update the sub-tree
      if (w == 0) {
        val t = transitions.find { it.first == s && it.second == a }
        // No such a transition, likely because:
        // 1. the search process terminated unexpectedly
        // 2. this transition is beyond the searching depth when generated, but may be reachable from the new initial
        if (t == null) {
          enabled[s]!![a] = steps - visited.size
        } else {
          val ss = t.third
          if (ss !in visited) {
            val subtotal = updateEnabled(ss, visited + s)
            if (subtotal > 0)
              enabled[s]!![a] = max(subtotal / 2, 1)  // divide by 2 to do a more BFS-like search
          }
        }
        totalW += enabled[s]!![a]!!
      } else {
        totalW += w
      }
    }
    return totalW
  }

  /**
   *
   */
  private fun dumpUI(retry: Int = 3): String {
    var dump: String? = null
    for (i in 1..retry) {
      dump = ADBHelper.uiautomatorDump()
      if (dump != null)
        break
      Thread.sleep(DumpDelayTime)
    }
    if (dump == null) {
      throw UIAutomatorFail()
    }
    return dump
  }

  /**
   *
   */
  private fun restartApp() {
    // shutdown the app and restart it to the given activity
    ADBHelper.shutdown(pkg)
    Thread.sleep(1000)
    ADBHelper.startActivity(pkg, activity)
    println("Restarting, wait 3 seconds for the app to initialize...")
    Thread.sleep(3000)
  }

  /**
   *
   */
  private fun search() {
    var s = 0
    var a = 0
    for (i in 0..steps) {
      // For the zero step, start the main activity
      if (s == 0) {
        assert(s == 0 && a == 0 && i == 0)
        restartApp()
      } else {  // Execute the planned action
        assert(a > 0)
        if (a == 1) {
          println("Step $i: No unvisited actions on state $s, press BACK button.")
          ADBHelper.backButton()
        } else {
          assert(a > 1)
          val act = alphabet[a]
          println("Step $i: search for action ${act.toShortName()} from state $s with weight ${enabled[s]!![a]}.")
          ADBHelper.input(act.toADBInput().split(" ").toTypedArray())
          // Update enabled map
          enabled[s]!![a] = 0
        }
        Thread.sleep(ActionDelayTime)
      }

      // Check if we are still in the component we want to test
      if (!ADBHelper.isFocusedApp(pkg)) {
        transitions.add(Transition(s, a, 0))
        println("Has left the system under test.")
        return
      }
      // Dump the current UI layout, retry for at most 3 times
      val dump = dumpUI()
      // Identify the current state or create a new state
      val ss = statesMap[dump]?: let {
        maxState++
        println("Find a new state $maxState.")
        // Update states map and initialize enabled map
        statesMap[dump] = maxState
        enabled[maxState] = mutableMapOf()

        // search fireable UI elements
        val parser = UIParser(dump, pkg)
        val actions = parser.parseFireableUI()
        if (actions.isEmpty()) {
          println("No fireable actions on this state.")
        } else {
          println("Find enabled actions for this state:")
        }
        // Add the actions to the alphabet and update the enabled map
        for (act in actions) {
          println("\t${act.toShortName()}")
          var newa = alphabet.indexOf(act)
          if (newa == -1) { // Add a new alphabet
            newa = alphabet.size
            alphabet.add(act)
          }
          // The default weight is the height of this node
          enabled[maxState]!![newa] = steps - i
        }

        maxState
      }
      // Update transitions
      transitions.add(Transition(s, a, ss))
      s = ss
      // If this is step 0 that just starts the main activity, update the weights in the enabled map
      if (i == 0)
        updateEnabled(s, emptySet())
      // Find next unvisited enabled action for this state or use back button
      a = enabled[s]!!.let { m -> m.keys.filter { m[it]!! > 0 }.maxBy { m[it]!! } }?: alphabet.indexOf(UIBackButton)
    }
  }

}
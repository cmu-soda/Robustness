package edu.cmu.isr.robust.fuzz.android

import edu.cmu.isr.robust.util.SimpleTransitions
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Transition
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.logging.LogManager
import java.util.logging.Logger

/**
 *
 */
const val ActionDelayTime: Long = 500

/**
 *
 */
const val DumpDelayTime: Long = 500

/**
 *
 */
class UIModelBuilder(val pkg: String, val activity: String,
                     val steps: Int = 100, val restart: Int = 10, val random: Boolean = false) {

  companion object {

    init {
      LogManager.getLogManager().readConfiguration(ClassLoader.getSystemResourceAsStream("logging.properties"))
    }

    private val logger = Logger.getLogger(UIModelBuilder::class.simpleName)
  }

  private class UIAutomatorFail: IllegalStateException("Failed to dump the current UI layout.")

  /**
   *
   */
  private val uiIndex = UIIndex()

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
    logger.info("Start searching with max depth: $steps, restart: $restart...")
    for (i in 1..restart) {
      try {
        search()
      } catch (e: UIAutomatorFail) {
        logger.warning("Needs retry due to: ${e.message}")
        ADBHelper.killServer()
      }

      logger.info("Number of states: ${maxState+1}")
      logger.info("Number of transitions: ${transitions.size}")
      logger.finer("Transitions: $transitions")
      logger.finer("Alphabet: ${alphabet.map { it.toShortName() }}")
      if (isComplete()) {
        logger.info("The search is completed in the sense that all the enabled actions are visited.")
        break
      } else {
        logger.finer("Enabled map: $enabled")
        logger.info("The search is not completed either because the max step is reached or the app leaves to home screen.")
        logger.info("Retry for the $i time...")
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
    var min = 0
    for (a in enabled[s]?.keys?: emptySet<Int>()) {
      val ss = transitions.filter { it.first == s && it.second == a }.map { it.third }
      if (ss.isEmpty()) {
        enabled[s]!![a] = visited.size
      } else {
        enabled[s]!![a] = ss.fold(0) { acc, it ->
          acc + if (it !in visited) updateEnabled(it, visited + it) else 0
        }
      }
      if (enabled[s]!![a]!! > 0)
        min = if (min == 0 || enabled[s]!![a]!! < min) enabled[s]!![a]!! else min
    }
    return min
  }

  /**
   *
   */
  private fun dumpUI(retry: Int = 5): String {
    var dump: String? = null
    for (i in 1..retry) {
      dump = ADBHelper.uiautomatorDump()
      if (dump != null)
        break
      logger.warning("uiautomator dump fail, retry for the $i time.")
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
    logger.info("Restarting, wait 3 seconds for the app to initialize...")
    Thread.sleep(3000)
  }

  /**
   *
   */
  private fun performAction(i: Int, s: Int, a: Int) {
    if (s == 0) {
      assert(s == 0 && a == 0 && i == 0)
      restartApp()
    } else {  // Execute the planned action
      assert(a > 0)
      if (a == 1) {
        logger.info("Step $i: No unvisited actions on state $s, press BACK button.")
        ADBHelper.backButton()
      } else {
        assert(a > 1)
        val act = alphabet[a]
        logger.info("Step $i: search for action ${act.toShortName()} from state $s.")
        ADBHelper.input(act.toADBInput().split(" ").toTypedArray())
        // Update enabled map
        enabled[s]!![a] = 0
      }
      Thread.sleep(ActionDelayTime)
    }
  }

  /**
   *
   */
  private fun isHomeOrCrash(s: Int, a: Int): Boolean {
    if (!ADBHelper.isFocusedApp(pkg)) {
      if (ADBHelper.isProcessAlive(pkg)) {
        transitions.add(Transition(s, a, 0))
        logger.warning("App has back to the home screen.")
      } else {
        transitions.add(Transition(s, a, -1))
        logger.severe("ERROR: App crashes.")
      }
      return true
    }
    return false
  }

  /**
   *
   */
  private fun identifyState(dump: String): Int {
    return uiIndex.get(dump) ?: let {
      maxState++
      logger.fine("Find a new state $maxState.")
      // Update states map and initialize enabled map
      uiIndex.put(dump, maxState)
      enabled[maxState] = mutableMapOf()

      // search fireable UI elements
      val parser = UIParser(dump)
      val actions = parser.parseFireableUI()
      if (actions.isEmpty()) {
        logger.fine("No fireable actions on this state.")
      } else {
        logger.fine("Find enabled actions for this state:")
      }
      // Add the actions to the alphabet and update the enabled map
      for (act in actions) {
        logger.fine("\t${act.toShortName()}")
        var newa = alphabet.indexOf(act)
        if (newa == -1) { // Add a new alphabet
          newa = alphabet.size
          alphabet.add(act)
        }
        enabled[maxState]!![newa] = 1
      }

      maxState
    }
  }

  /**
   *
   */
  private fun search() {
    var s = 0
    var a = 0
    for (i in 0..steps) {
      // For the zero step, start the main activity
      performAction(i, s, a)
      // Check if we are still in the component we want to test
      if (isHomeOrCrash(s, a))
        return
      // Identify the current state by dumping the UI layout
      s = let {
        val ss = identifyState(dumpUI())
        // Update transitions
        transitions.add(Transition(s, a, ss))
        ss
      }
      // If this is step 0 that just starts the main activity, then update the enabled map in order to search
      // the <state, action> pair in deep
      if (i == 0) {
        updateEnabled(s, setOf(s))
        logger.finer("Enabled map at the first screen: $enabled")
      }

//      val existTrans = transitions.filter { it.first == s && it.second == a }
//      s = if (s != 0 && existTrans.size == 1) { // Important: ignore the transition from home screen to starting the app
//        // TODO: This is a kind of abstraction. It is possible that although the UI layout is the same, the underlying
//        //  state has changed. Thus, the same transition may lead to different state. In LTS, we can model this by
//        //  nondeterminism at this state s. We can also choose two merge the nondeterministic states into one.
//        logger.info("Transition already exists, skip the dump process.")
//        existTrans.first().third
//      } else {
//        // Dump the current UI layout, retry for at most 3 times
//        val dump = dumpUI()
//        // Identify the current state or create a new state
//        val ss = identifyState(dump)
//        // Update transitions
//        transitions.add(Transition(s, a, ss))
//        ss
//      }

      // Find next unvisited enabled action for this state or use back button
      a = if (random) {
        val ks = enabled[s]!!.let { e -> e.keys.filter { e[it]!! > 0 } }
        if (ks.isEmpty())
          alphabet.indexOf(UIBackButton)
        else
          ks.random()
      } else {
        enabled[s]!!.let { e -> e.keys.filter { e[it]!! > 0 }.minBy { e[it]!! } } ?: alphabet.indexOf(UIBackButton)
      }
    }
  }

}
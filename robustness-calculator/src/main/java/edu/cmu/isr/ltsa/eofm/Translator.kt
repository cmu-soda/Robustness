package edu.cmu.isr.ltsa.eofm

class EOFMTranslator(eofms: EOFMS, initValues: Map<String, String>) {

  /**
   * The list of all the constants defined in the EOFM. These constants will be translated to LTSA constants. In LTSA,
   * constants should be numbers.
   */
  private val consts: List<Constant> = eofms.constants
  /**
   * The list of all the user defined types in EOFM. Each type is translated to a set of constants and a range.
   * For instance, 'Bool == {True, False}' will be translated to 'const True = 1 const False = 0
   * range Bool = False..True'.
   */
  private val userDefinedTypes: List<UserDefineType> = eofms.userDefinedTypes
  /**
   * The list of all the human actions. Right these actions are only used to define the actions exposed.
   */
  private val actions: List<HumanAction> = eofms.humanOperators.flatMap { it.humanActions }
  /**
   * The list of all the input variables. This is useful in the case of input variable links.
   */
  private val inputVariables: List<InputVariable> = eofms.humanOperators.flatMap { it.inputVariables }
  /**
   * The list of all the top-level activities. The translation will start from all the top-level activities.
   *
   * TODO: Right now, top-level activities are the activities directly under the <eofm> node. However, each human
   * operator could have multiple eofms, and an EOFM model can have multiple human operators. I have not decided whether
   * to generate a single LTSA file for each top-level activity because the generated model is large even for a simple
   * top-level activity.
   */
  private val topLevelActivities: List<Activity> = eofms.humanOperators.flatMap { it.eofms.map { eofm -> eofm.activity } }
  /**
   * A map of all the activities by name. This is useful in the case of activity links.
   */
  private val activities: MutableMap<String, Activity> = mutableMapOf()

  init {
    fun recursive(activity: Activity) {
      activities[activity.name] = activity
      for (it in activity.decomposition.subActivities) {
        if (it is Activity) {
          recursive(it)
        }
      }
    }

    // Find all the activities defined
    for (humanOperator in eofms.humanOperators) {
      for (eofm in humanOperator.eofms) {
        recursive(eofm.activity)
      }
    }
    /**
     * Set the initial value for input variables. This is not defined in the paper. However, I find it necessary. No
     * matter in event-based model like LTSA or state-based model like TLA+, we all need to define the initial state
     * of the model
     */
    for (it in inputVariables) {
      it.initialValue = initValues[it.name] ?: error("The initial value for ${it.name} is not presented")
    }
  }

  /**
   * The process to translate a EOFM model to LTSA.
   */
  fun translate(builder: StringBuilder, processes: MutableList<String>) {
    val menu = actions.map { it.name } + inputVariables.map { "set_${it.name}[${it.userDefinedType}]" } +
        "turn[Turn]"

    // Build the constants for the activity/action state
    builder.append("const Ready = 0\n")
    builder.append("const Executing = 1\n")
    builder.append("const Done = 2\n")
    builder.append("range ActState = Ready..Done\n\n")

    builder.append("const System = 0\n")
    builder.append("const Human = 1\n")
    builder.append("range Turn = System..Human\n\n")

    // Append all the EOFM constants
    for (it in consts)
      builder.appendConstant(it)
    builder.append('\n')

    // Append all the user defined types
    for (it in userDefinedTypes)
      builder.appendUserDefinedType(it)

    // Append a helper menu of LTSA
    builder.append("menu HDI = {${menu.joinToString()}}\n\n")

    /**
     * Start to translate all the top-level activities and their sub-activities recursively. Note that, the same
     * activity (e.g., activity associated through the link) is translated to a different LTSA process, because its
     * parent and siblings change.
     */
    for (i in topLevelActivities.indices)
      ActivityTranslator(topLevelActivities[i], inputVariables, activities, postfix = "$i").translate(builder, processes)

    /**
     * Append the composition process.
     * TODO: If each top-level activity is translated into different files. Then the name for this composition process
     * should change.
     */
    builder.append("||EOFM = (\n\t")
    builder.append(processes.joinToString("\n||\t"))
    builder.append("\n)@{")
    builder.append(menu.joinToString(",\n"))
    builder.append("}.")
  }

  /**
   * Each constant in EOFM is translated to a constant in LTSA. Limitation: constants in LTSA should be numbers.
   */
  private fun StringBuilder.appendConstant(constant: Constant) {
    this.append("const ${constant.name} = ${constant.value}\n")
  }

  /**
   * Each user defined type in EOFM is translated to a set of constants and a range in LTSA. E.g.,
   * 'Bool == {True, False}' will be translated to 'const False = 0 const True = 1 range Bool = False..True'
   */
  private fun StringBuilder.appendUserDefinedType(userDefineType: UserDefineType) {
    val value = userDefineType.value
    val consts = value.substring(1 until value.length - 1).split(""",\s*""".toRegex())
    for (i in consts.indices)
      this.append("const ${consts[i]} = $i\n")
    this.append("range ${userDefineType.name} = ${consts[0]}..${consts[consts.size - 1]}")
    this.append("\n\n")
  }

}

/**
 * The abstract translator for activity and action. It defines some common translation rules.
 */
private abstract class ActTranslator(
    name: String, val parOp: String?, val parent: String?, val preSibling: String?, val siblings: List<String>,
    val postfix: String
) {
  /**
   * The process name of this activity. A process name in LTSA should be capitalizied (e.g., ABrewCoffee).
   */
  protected val processName = "${name.capitalize()}$postfix"
  /**
   * The event name of this activity used to define transitions (e.g., set_aBrewCoffee)
   */
  protected val eventName = "${name}$postfix"
  /**
   * The variables for the LTSA process.
   */
  protected var processVars = if (parent != null)
    listOf("turn", "self", parent, "reset") + siblings
  else
    listOf("turn", "self") + siblings

  abstract fun translate(builder: StringBuilder, processes: MutableList<String>)
  abstract fun StringBuilder.appendProcessHeader()

  protected fun StringBuilder.appendProcessInit() {
    if (parent != null)
      this.append("P_$processName = $processName[Human][Ready][Ready][0]")
    else
      this.append("P_$processName = $processName[Human][Ready]")
    for (it in siblings)
      this.append("[Ready]")
  }

  protected fun StringBuilder.appendParamProcess() {
    this.append("${processName}[turn:Turn][self:ActState]")
    if (parent != null)
      this.append("[$parent:ActState][reset:0..1]")
    for (it in siblings)
      this.append("[$it:ActState]")
  }

  protected abstract fun StringBuilder.appendReadyToExecuting()
  protected abstract fun StringBuilder.appendExecutingToDone()

  /**
   * TODO:
   */
  protected fun StringBuilder.appendDoneToReady() {
    this.append("\t\t// Done to Ready (reset)\n")
    if (parent == null) {
      this.append("\t\t// No parent, by default true.\n")
      this.append("\t|\twhen (turn == Human && self == Done)\n")
      this.append("\t\t\tset_$eventName[Ready] -> ")
      this.appendProcessVars("self" to "Ready")
    } else {
      this.append("\t\t// Should synchronize on the parent's reset condition\n")
      this.append("\t|\twhen (turn == Human && self == Done && $parent == Done)\n")
      this.append("\t\t\tset_$parent[Ready] -> ")
      this.appendProcessVars(parent to "Ready", "reset" to "1")

      this.append("\t|\twhen (turn == Human && self == Done && $parent == Executing)\n")
      this.append("\t\t\tset_$parent[Executing] -> ")
      this.appendProcessVars(parent to "Executing", "reset" to "1")

      this.append("\t|\twhen (turn == Human && self == Done && reset)\n")
      this.append("\t\t\tset_$eventName[Ready] -> ")
      this.appendProcessVars("self" to "Ready", "reset" to "0")
    }
  }

  protected fun StringBuilder.appendStartCondition() {
    if (parent != null)
      this.append(" && $parent == Executing")

    if (parOp != null) {
      when (parOp) {
        "and_par", "or_par", "optor_par", "sync" -> return
        "ord" -> if (preSibling != null) this.append(" && $preSibling == Done")
        "xor" -> for (it in siblings) this.append(" && $it == Ready")
        else -> for (it in siblings) this.append(" && $it != Executing")
      }
    }
  }

  protected abstract fun StringBuilder.appendEndCondition()

  protected fun StringBuilder.appendProcessVars(vararg replaces: Pair<String, String>) {
    this.append(processName)
    var s = processVars.joinToString("") { "[$it]" }
    for (it in replaces) {
      s = s.replace(it.first, it.second)
    }
    this.append(s)
    this.append('\n')
  }

  protected fun StringBuilder.appendSyncParent() {
    if (parent != null) {
      this.append("\t|\twhen (turn == Human && !(self == Done && $parent == Executing))\n")
      this.append("\t\t\tset_$parent[Executing] -> ")
      this.appendProcessVars(parent to "Executing")

      this.append("\t|\twhen (turn == Human)\n")
      this.append("\t\t\tset_$parent[Done] -> ")
      this.appendProcessVars(parent to "Done")

      this.append("\t|\twhen (turn == Human && !(self == Done && $parent == Done))\n")
      this.append("\t\t\tset_$parent[Ready] -> ")
      this.appendProcessVars(parent to "Ready")
    }
  }

  protected fun StringBuilder.appendSyncSiblings() {
    for (s in siblings) {
      this.append("\t\t// sibling $s\n")
      this.append("\t|\twhen (turn == Human)\n")
      this.append("\t\t\tset_$s[i:ActState] -> ")
      this.appendProcessVars(s to "i")
    }
  }

  protected fun StringBuilder.appendSyncTurnChange() {
    this.append("\t|\twhen(turn == Human) turn[System] -> ")
    this.appendProcessVars("turn" to "System")
    this.append("\t|\twhen (turn == System) turn[Human] -> ")
    this.appendProcessVars("turn" to "Human")
  }
}

/**
 * TODO:
 */
private class ActivityTranslator(
    val act: Activity, val inputVariables: List<InputVariable>, val activities: Map<String, Activity>,
    parOp: String? = null, parent: String? = null, preSibling: String? = null, siblings: List<String> = emptyList(),
    postfix: String = ""
) : ActTranslator(act.name, parOp, parent, preSibling, siblings, postfix) {

  /**
   * Get all the input variables related to the activity. According to EOFM's activity state transition rules, each
   * activity process should keep track of the input variables associated to its preconditions, repeat-conditions, and
   * completion-conditions.
   */
  private val inputs = inputVariables.filter { i ->
    act.preConditions.find { it.indexOf(i.name) != -1 } != null ||
        act.completionConditions.find { it.indexOf(i.name) != -1 } != null ||
        act.repeatConditions.find { it.indexOf(i.name) != -1 } != null
  }
  /**
   * Get the decomposition operator. This operator will affect the start-condition and end-condition of its child
   * activities.
   */
  private val op = act.decomposition.operator

  /**
   * Get the names of all its sub-activities. Each activity process should keep track of the execution state of its
   * child-activities. They are used to determine the start-and-end-condition.
   */
  private val subacts = act.decomposition.subActivities.map {
    when (it) {
      is Activity -> it.name
      is ActivityLink -> activities[it.link]?.name
          ?: throw IllegalArgumentException("No activity named ${it.link} found")
      is Action -> it.humanAction
      else -> throw IllegalArgumentException("Unknown type of activity/action in decomposition.")
    }
  }.mapIndexed { idx, s -> "$s$postfix$idx" }

  override fun translate(builder: StringBuilder, processes: MutableList<String>) {
    processes.add("P_$processName")
    // Append process header, and set the list of process variables for later user
    builder.appendProcessHeader()
    // From Ready to Executing
    builder.appendReadyToExecuting()
    // From Ready to Done
    builder.appendReadyToDone()
    // From Executing to Executing
    builder.appendExecutingToExecuting()
    // From Executing to Done
    builder.appendExecutingToDone()
    // From Done to Ready (reset)
    builder.appendDoneToReady()
    // Synchronize on parent activity
    builder.appendSyncParent()
    // Synchronize on siblings activities
    builder.appendSyncSiblings()
    // Synchronize on sub-activities
    builder.appendSyncSubActs()
    // Synchronize on variable value changes. IMPORTANT: variables changes should only happen in the machine's turn.
    builder.appendSyncInputs()
    // Synchronize on turn change.
    builder.appendSyncTurnChange()
    // Append process ending
    builder.append(").\n\n")

    // Append sub-activities
    for (i in subacts.indices) {
      when (val subact = act.decomposition.subActivities[i]) {
        is Activity -> ActivityTranslator(
            act = subact,
            inputVariables = inputVariables,
            activities = activities,
            postfix = postfix + i,
            parOp = op,
            parent = eventName,
            preSibling = if (i > 0) subacts[i - 1] else null,
            siblings = subacts.filterIndexed { index, _ -> index != i }
        ).translate(builder, processes)
        is ActivityLink -> ActivityTranslator(
            act = activities[subact.link] ?: error("The activity ${subact.link} is not found!"),
            inputVariables = inputVariables,
            activities = activities,
            postfix = postfix + i,
            parOp = op,
            parent = eventName,
            preSibling = if (i > 0) subacts[i - 1] else null,
            siblings = subacts.filterIndexed { index, _ -> index != i }
        ).translate(builder, processes)
        is Action -> ActionTranslator(
            act = subact,
            postfix = postfix + i,
            parOp = op,
            parent = eventName,
            preSibling = if (i > 0) subacts[i - 1] else null,
            siblings = subacts.filterIndexed { index, _ -> index != i }
        ).translate(builder, processes)
      }
    }
  }

  override fun StringBuilder.appendProcessHeader() {
    this.appendProcessInit()
    // Append sub-activities init after the default init
    for (it in subacts)
      this.append("[Ready]")
    // Append variables init after the default init
    for (it in inputs)
      this.append("[${it.initialValue}]")
    this.append(",\n")

    // Update the default process vars
    processVars = processVars + subacts + inputs.map { it.name }

    // Append process header
    this.appendParamProcess()
    // Append parameters for sub-activities and input variables
    for (it in subacts)
      this.append("[$it:ActState]")
    for (it in inputs)
      this.append("[${it.name}:${it.userDefinedType}]")
    this.append(" = (\n")
  }

  override fun StringBuilder.appendReadyToExecuting() {
    this.append("\t\t// Ready to Executing\n")
    this.append("\t\twhen (turn == Human && self == Ready")
    this.appendStartCondition()
    this.appendPrecondition()
    this.appendCompletionCondition(false)
    this.append(")\n\t\t\tset_$eventName[Executing] -> ")
    this.appendProcessVars("self" to "Executing")
  }

  private fun StringBuilder.appendSyncSubActs() {
    for (sub in subacts) {
      this.append("\t\t// sub-activity $sub\n")
      this.append("\t|\twhen (turn == Human)\n")
      this.append("\t\t\tset_$sub[i:ActState] -> ")
      this.appendProcessVars(sub to "i")
    }
  }

  private fun StringBuilder.appendSyncInputs() {
    for (v in inputs) {
      this.append("\t\t// input ${v.name}\n")
      this.append("\t|\twhen (turn == System)\n")
      this.append("\t\t\tset_${v.name}[i:${v.userDefinedType}] -> ")
      this.appendProcessVars(v.name to "i")
    }
  }

  private fun StringBuilder.appendReadyToDone() {
    this.append("\t\t// Ready to Done\n")
    this.append("\t|\twhen (turn == Human && self == Ready")
    this.appendStartCondition()
    this.appendCompletionCondition()
    this.append(")\n\t\t\tset_$eventName[Done] -> ")
    this.appendProcessVars("self" to "Done")
  }

  private fun StringBuilder.appendExecutingToExecuting() {
    this.append("\t\t// Executing to Executing\n")
    this.append("\t|\twhen (turn == Human && self == Executing")
    this.appendEndCondition()
    this.appendRepeatCondition()
    this.appendCompletionCondition(false)
    this.append(")\n\t\t\tset_$eventName[Executing] -> ")
    this.appendProcessVars("self" to "Executing")
  }

  override fun StringBuilder.appendExecutingToDone() {
    this.append("\t\t// Executing to Done\n")
    this.append("\t|\twhen (turn == Human && self == Executing")
    this.appendEndCondition()
    this.appendCompletionCondition()
    this.append(")\n\t\t\tset_$eventName[Done] -> ")
    this.appendProcessVars("self" to "Done")
  }

  override fun StringBuilder.appendEndCondition() {
    for (it in subacts) this.append(" && $it != Executing")
    when (op) {
      "and_seq", "and_par", "sync" -> for (it in subacts) this.append(" && $it == Done")
      "or_seq", "or_par", "xor" -> {
        this.append(" && (")
        this.append(subacts.joinToString(" || ") { "$it == Done" })
        this.append(")")
      }
      "optor_seq", "optor_par" -> return
      "ord" -> if (subacts.isNotEmpty()) this.append(" && ${subacts.last()} == Done")
      else -> throw java.lang.IllegalArgumentException("Cannot recognize operator: $op")
    }
  }

  private fun StringBuilder.appendPrecondition() {
    for (it in act.preConditions)
      this.append(" && $it")
  }

  private fun StringBuilder.appendCompletionCondition(sign: Boolean = true) {
    if (!sign && act.completionConditions.isNotEmpty()) {
      this.append(" && !(")
      this.append(act.completionConditions.joinToString(" && "))
      this.append(")")
    } else {
      for (it in act.completionConditions)
        this.append(" && $it")
    }
  }

  private fun StringBuilder.appendRepeatCondition() {
    for (it in act.repeatConditions)
      this.append(" && $it")
  }
}

private class ActionTranslator(
    val act: Action,
    parOp: String, parent: String, preSibling: String? = null, siblings: List<String> = emptyList(),
    postfix: String = ""
) : ActTranslator(act.humanAction ?: error("No human action assigned to this action node!, '$act'"),
    parOp, parent, preSibling, siblings, postfix) {

  override fun translate(builder: StringBuilder, processes: MutableList<String>) {
    processes.add("P_$processName")
    // Append process header, and set the list of process variables for later user
    builder.appendProcessHeader()
    // From Ready to Executing
    builder.appendReadyToExecuting()
    // From Executing to Done
    builder.appendExecutingToDone()
    // From Done to Ready (reset)
    builder.appendDoneToReady()
    // Synchronize on parent activity
    builder.appendSyncParent()
    // Synchronize on siblings activities
    builder.appendSyncSiblings()
    // Synchronize on turn change.
    builder.appendSyncTurnChange()
    // Append process ending
    builder.append(").\n\n")
  }

  override fun StringBuilder.appendProcessHeader() {
    this.appendProcessInit()
    this.append(",\n")
    // Append process header
    this.appendParamProcess()
    this.append(" = (\n")
  }

  override fun StringBuilder.appendReadyToExecuting() {
    this.append("\t\t// Ready to Executing\n")
    this.append("\t\twhen (turn == Human && self == Ready")
    this.appendStartCondition()
    this.append(")\n\t\t\tset_$eventName[Executing] -> ")
    this.appendProcessVars("self" to "Executing")
  }

  override fun StringBuilder.appendExecutingToDone() {
    this.append("\t\t// Executing to Done\n")
    this.append("\t|\twhen (turn == Human && self == Executing")
    this.appendEndCondition()
    // Perform the human action and then wait for the environment to change, i.e., changes to System turn.
    this.append(")\n\t\t\t${act.humanAction} -> turn[System] -> turn[Human] -> set_$eventName[Done] -> ")
    this.appendProcessVars("self" to "Done")
  }

  override fun StringBuilder.appendEndCondition() {
    return
  }
}
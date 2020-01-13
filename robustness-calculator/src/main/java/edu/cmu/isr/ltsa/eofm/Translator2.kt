package edu.cmu.isr.ltsa.eofm

class EOFMTranslator2(eofms: EOFMS, initValues: Map<String, String>) {
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
  fun translate(builder: StringBuilder) {
    // Append all the EOFM constants
    for (it in consts)
      builder.appendConstant(it)
    builder.append('\n')

    // Append all the user defined types
    for (it in userDefinedTypes)
      builder.appendUserDefinedType(it)

    // Append all the top-level activities
    for (it in topLevelActivities)
      builder.appendActivity(it)
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

  /**
   * @return The name of the LTSA process.
   */
  private fun StringBuilder.appendActivity(activity: Activity, ancestors: List<Activity> = emptyList()): String {
    // Name of the translated process (should be capitalized)
    val name = activity.name.capitalize()
    // The list of ancestors passed to children activities/actions
    val nextAncestors = ancestors + listOf(activity)
    // The list of all the sub-activities/actions
    val subActivities = activity.decomposition.subActivities
    // The name of all the processes to compose
    val processes = mutableListOf<String>()

    // If the activity has only one child action, we treat it as a leaf activity and we don't translate the
    // decomposition operator because it is always 'ord'.
    var isLeaf = subActivities.size == 1
    for (it in subActivities) {
      when (it) {
        is Activity -> {
          processes.add(this.appendActivity(it, nextAncestors))
          isLeaf = false
        }
        is Action -> processes.add(this.appendAction(it, nextAncestors))
        is ActivityLink -> processes.add(this.appendActivity(
            activities[it.link] ?: error("Unknown activity link '${it.link}'"), nextAncestors
        ))
        else -> throw error("Unknown child for activity ${activity.name}")
      }
    }

    // Translate the decomposition operator if this node is not a leaf activity.
    if (!isLeaf)
      processes.add(this.appendOperator(activity.decomposition.operator, subActivities, nextAncestors))

    // If this activity has any conditions, then translate the conditions as one LTSA process.
    if (activity.preConditions.isNotEmpty() || activity.repeatConditions.isNotEmpty() || activity.completionConditions.isNotEmpty())
      processes.add(this.appendCondition(activity))

    // This activity is the parallel composition of all its sub-activity/action processes, operator process,
    // and condition process.
    this.append("||$name = (${processes.joinToString(" || ")}).\n\n")
    return name
  }

  /**
   * @return The LTSA process name of this action.
   */
  private fun StringBuilder.appendAction(action: Action, ancestors: List<Activity>): String {
    assert(action.humanAction != null)

//    val actName = ancestors.joinToString("_") { it.name.capitalize() } +
//        "_${action.humanAction!!.capitalize()}"
    // Process name of this action (should be capitalized).
    val actName = action.humanAction!!.capitalize()

    /**
     * A helper function to append the action process.
     */
    fun helper(i: Int) {
      // End condition, all the ancestor activities have been translated.
      if (i == ancestors.size) {
        this.append("ACT = (${action.humanAction} -> sys -> human -> END_REPEAT_${ancestors[i - 1].name.capitalize()}")
        // Append turn change
        this.append(" | sys -> human -> ACT")
        this.append("),\n")
        return
      }

      val name = ancestors[i].name.capitalize()
      // Append "start each activity"
      if (i == 0)
        this.append("$actName = (start_$name -> ")
      else
        this.append("$name = (start_$name -> ")
      // Append the next process to go
      if (i + 1 < ancestors.size)
        this.append(ancestors[i + 1].name.capitalize())
      else
        this.append("ACT")
      // Append skip this activity. Cannot skip the root activity
      if (i > 0)
        this.append(" | skip_$name -> END_REPEAT_${ancestors[i - 1].name.capitalize()}")
      // Append turn change
      if (i == 0)
        this.append(" | sys -> human -> $actName")
      else
        this.append(" | sys -> human -> $name")
      this.append("),\n")

      // recursively append this next ancestor
      helper(i + 1)

      this.append("END_REPEAT_$name = (")
      // Append repeat, restart the sub-activity
      if (i + 1 < ancestors.size)
        this.append("repeat_$name -> ${ancestors[i + 1].name.capitalize()}")
      else
        this.append("repeat_$name -> ACT")
      // If this is the root activity, it can be reset
      if (i > 0) {
        this.append(" | end_$name -> END_REPEAT_${ancestors[i - 1].name.capitalize()}")
        this.append(" | sys -> human -> END_REPEAT_$name")
        this.append("),\n")
      } else {
        this.append(" | end_$name -> reset_$name -> $actName")
        this.append(" | sys -> human -> END_REPEAT_$name")
        this.append(").\n\n")
      }
    }

    helper(0)
    return actName
  }

  /**
   *
   */
  private fun StringBuilder.appendOperator(operator: String, subActivities: List<Any>, ancestors: List<Activity>): String {
    // Names of the sub-activities
    val subNames = subActivities.map {
      when (it) {
        is Activity -> it.name.capitalize()
        is ActivityLink -> it.link.capitalize()
        else -> error("Unknown type of sub-activity '$it'")
      }
    }
    // Name of this operator process
    val name = operator.toUpperCase() + "_" + subNames.joinToString("_")
    // The code snippet for skipping sub-activities
    val skips = subNames.joinToString(", ") { "skip_$it" }

    this.append("$name = (")
    when (operator) {
      "ord" -> {
        for (it in subNames) {
          this.append("start_$it -> end_$it -> ")
        }
        this.append(name)
        this.append(")+{$skips}.\n\n")
      }
      "and_seq" -> {
        this.append(subNames.joinToString(" | ") { "start_$it -> WAIT" })
        this.append("),\n")
        this.append("WAIT = (")
        this.append(subNames.joinToString(" | ") { "end_$it -> $name" })
        this.append(")+{$skips}.\n\n")
      }
      "and_par" -> {
        this.setLength(this.length - 1)
        this.append("END+{$skips}.\n\n")
      }
      "or_seq", "or_par" -> {
        when (operator) {
          "or_seq" -> {
            this.append(subNames.joinToString(" | ") { "start_$it -> WAIT" })
            this.append("),\n")
            this.append("WAIT = (")
            this.append(subNames.joinToString(" | ") { "end_$it -> SKIP" })
            this.append("),\n")
          }
          "or_par" -> {
            this.append(subNames.joinToString(" | ") { "start_$it -> SKIP" })
            this.append("),\n")
          }
        }
        this.append("SKIP = (")
        this.append(subNames.joinToString(" | ") { "start_$it -> WAIT" })
        this.append(" | ")
        this.append(subNames.joinToString(" | ") { "skip_$it -> SKIP" })
        this.append(" | ")
        this.append(ancestors.joinToString(" | ") { "repeat_${it.name.capitalize()} -> $name" })
        this.append(" | reset_${ancestors[0].name.capitalize()} -> $name")
        this.append(").\n\n")
      }
      "optor_seq" -> {
        this.append(subNames.joinToString(" | ") { "start_$it -> WAIT" })
        this.append(" | ")
        this.append(subNames.joinToString(" | ") { "skip_$it -> $name" })
        this.append("),\n")
        this.append("WAIT = (")
        this.append(subNames.joinToString(" | ") { "end_$it -> $name" })
        this.append(").\n\n")
      }
      "optor_par" -> {
        this.setLength(this.length - 1)
        this.append("END.\n\n")
      }
      "xor" -> {
        this.append(subNames.joinToString(" | ") { "start_$it -> SKIP" })
        this.append("),\n")
        this.append("SKIP = (")
        this.append(subNames.joinToString(" | ") { "skip_$it -> SKIP" })
        this.append(" | ")
        this.append(ancestors.joinToString(" | ") { "repeat_${it.name.capitalize()} -> $name" })
        this.append(" | reset_${ancestors[0].name.capitalize()} -> $name")
        this.append(").\n\n")
      }
      else -> throw IllegalArgumentException("$operator is not supported")
    }

    return name
  }

  /**
   * @return The name of LTSA process.
   */
  private fun StringBuilder.appendCondition(activity: Activity): String {
    val name = activity.name.capitalize()
    val condName = name + "_COND"
    val human = "HUMAN"
    val sys = "SYS"

    // Get all the input variables related to the activity.
    val inputs = activity.getInputs(inputVariables)
    val variables = inputs.joinToString("") { "[${it.name}]" }

    // Append the process name and its initial value
    this.append("$condName = $human")
    this.append(inputs.joinToString("") { "[${it.initialValue}]" })
    this.append(",\n")
    // Append the parameterized process
    this.append(human)
    this.append(inputs.joinToString("") { "[${it.name}:${it.userDefinedType}]" })
    this.append(" = (\n")

    // Append conditions to start this activity
    var bar = "\t\t"
    if (activity.preConditions.isNotEmpty() || activity.completionConditions.isNotEmpty()) {
      this.append(bar)
      bar = "\t|\t"

      this.append("when (${activity.preConditions.joinToString(" && ")}")
      if (activity.preConditions.isNotEmpty())
        this.append(" && ")
      this.append("!(${activity.completionConditions.joinToString(" && ")}))\n")
      this.append("\t\t\tstart_$name -> $human$variables\n")
    }

    // Append conditions to repeat this activity
    if (activity.repeatConditions.isNotEmpty() || activity.completionConditions.isNotEmpty()) {
      this.append(bar)
      bar = "\t|\t"

      this.append("when (${activity.repeatConditions.joinToString(" && ")}")
      if (activity.repeatConditions.isNotEmpty())
        this.append(" && ")
      this.append("!(${activity.completionConditions.joinToString(" && ")}))\n")
      this.append("\t\t\trepeat_$name -> $human$variables\n")
    }

    // Append conditions to complete this activity
    if (activity.completionConditions.isNotEmpty()) {
      this.append(bar)
      bar = "\t|\t"

      this.append("when (${activity.completionConditions.joinToString(" && ")})\n")
      this.append("\t\t\tend_$name -> $human$variables\n")
    }

    // Append synchronizations on input variables change
    this.append(bar)
    this.append("sys -> $sys$variables\n),\n")
    this.append(sys)
    this.append(inputs.joinToString("") { "[${it.name}:${it.userDefinedType}]" })
    this.append(" = (\n")
    bar = "\t\t"

    for (it in inputs) {
      this.append(bar)
      bar = "\t|\t"

      val x = variables.replace(it.name, "x")
      this.append("set_${it.name}[x:${it.userDefinedType}] -> $sys$x\n")
    }
    this.append(bar)
    this.append("human -> $human$variables\n")
    this.append(").\n\n")

    return condName
  }
}
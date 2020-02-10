package edu.cmu.isr.ltsa.eofm

class EOFMTranslator2(
    eofms: EOFMS,
    initValues: Map<String, String>,
    /**
     * The world model which describes how the 'physical world' should change. We assume that this model is predefined
     * and should not contain error. And the mapping from world to human/system should also be correct. It means that
     * we omit the state mismatches between the world and human/system.
     */
    private val world: List<String>,
    /**
     * Labels to rename when composing with the machine specification.
     */
    private val relabels: Map<String, String> = emptyMap()
) {
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
   * The list of all the human actions. Right now, these actions are only used to define the actions exposed.
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

  /**
   * The list of activities that are translated when translating a top-level activity. When translating with error,
   * we need these names to append the priority of '<error>_<activity>' events.
   */
  private val translatedActivities: MutableList<String> = mutableListOf()
  private val translatedActions: MutableList<String> = mutableListOf()

  /**
   * The flag indicating whether injecting errors (omission, commission, repetition) to the translation.
   */
  private var withError: Boolean = false

  var errorTypes: List<String> = emptyList()
    private set

  init {
    // Recursively find all the activities
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
   *
   */
  fun getActions(): List<String> {
    return actions.map { relabels[it.name] ?: it.name }
  }

  /**
   * The process to translate a EOFM model to LTSA.
   */
  fun translate(builder: StringBuilder, withError: Boolean = false) {
    this.withError = withError

    // Append all the EOFM constants
    for (it in consts)
      builder.appendConstant(it)
    builder.append('\n')

    // Append all the user defined types
    for (it in userDefinedTypes)
      builder.appendUserDefinedType(it)

    // Append all the top-level activities
    translatedActivities.clear()
    translatedActions.clear()
    val topLevelNames = topLevelActivities.map { builder.appendActivity(it) }
    builder.append("||ENV = (${topLevelNames.joinToString(" || ")})")
    if (withError) {
      val errors = translatedActivities.map { listOf("commission_$it", "repetition_$it", "omission_$it") }
      errorTypes = errors.flatten()
      builder.append("<<{${errors.joinToString(",\n") { it.joinToString(",") }}\n}.\n")
    } else {
      builder.append(".\n")
    }
  }

  private fun genActivityProcessName(activity: Activity): String {
    val name = activity.name.capitalize()
    val i = translatedActivities.count { it == name }
    return if (i == 0) name else "$name$i"
  }

  private fun genActionProcessName(action: Action): String {
    val name = action.humanAction!!.capitalize()
    val i = translatedActions.count { it == name }
    return if (i == 0) name else "$name$i"
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
  private fun StringBuilder.appendActivity(activity: Activity, ancestors: List<String> = emptyList()): String {
    // Name of the translated process (should be capitalized)
    val name = genActivityProcessName(activity)
    translatedActivities.add(name)
    // The list of ancestors passed to children activities/actions
    val nextAncestors = ancestors + name
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
      processes.add(this.appendOperator(activity.decomposition.operator, processes, name))

    // If this activity has any conditions, then translate the conditions as one LTSA process.
    if (activity.preConditions.isNotEmpty() || activity.repeatConditions.isNotEmpty() || activity.completionConditions.isNotEmpty())
      processes.add(this.appendCondition(name, activity.preConditions, activity.repeatConditions, activity.completionConditions))

    // This activity is the parallel composition of all its sub-activity/action processes, operator process,
    // and condition process.
    this.append("||$name = (${processes.joinToString(" || ")}).\n\n")
    return name
  }

  /**
   * @return The LTSA process name of this action.
   */
  private fun StringBuilder.appendAction(action: Action, ancestors: List<String>): String {
    assert(action.humanAction != null)

    // Process name of this action (should be capitalized).
    val actName = genActionProcessName(action)
    translatedActions.add(actName)

    /*
     * A helper function to append the action process. For an action A in the following hierarchy, P1 -> P2 -> A
     * it should be translated as follows:
     *
     * A = (start_P1 -> P2 | end_P1 -> reset_P1 -> A),
     * P2 = (start_P2 -> ACT | end_P2 -> END_REPEAT_P1 | skip_P2 -> END_REPEAT_P1),
     * ACT = (action -> END_REPEAT_P2),
     * END_REPEAT_P2 = (repeat_P2 -> ACT | end_P2 -> END_REPEAT_P1),
     * END_REPEAT_P1 = (repeat_P1 -> P2 | end_P1 -> reset_P1 -> A).
     *
     */
    fun helper(i: Int) {
      /*
       * End condition, all the ancestor activities have been translated. Then,
       * ACT = (action -> END_REPEAT_<parent>)
       */
      if (i == ancestors.size) {
        this.append("ACT = (${action.humanAction} -> END_REPEAT_${ancestors[i - 1]}")
        this.append("),\n")
        return
      }

      val name = ancestors[i]
      /*
       * Append "start each activity", i.e.,
       * A = (start_P1 -> P2
       * or
       * P2 = (start_P2 -> ACT
       */
      if (i == 0)
        this.append("$actName = (start_$name -> ")
      else
        this.append("$name = (start_$name -> ")
      if (i + 1 < ancestors.size)
        this.append(ancestors[i + 1])
      else
        this.append("ACT")

      /*
       * Append directly end this activity from ready state, i.e.,
       * end_P1 -> reset_P1 -> A
       * or
       * end_P2 -> END_REPEAT_P1
       */
      if (i > 0)
        this.append(" | end_$name -> END_REPEAT_${ancestors[i - 1]}")
      else
        this.append(" | end_$name -> reset_$name -> $actName")

      /*
       * Append skip this activity. Cannot skip the root activity, i.e.,
       * skip_P2 -> END_REPEAT_P1
       */
      if (i > 0)
        this.append(" | skip_$name -> END_REPEAT_${ancestors[i - 1]}")

      this.append("),\n")

      // recursively append the next ancestor
      helper(i + 1)

      this.append("END_REPEAT_$name = (")
      /*
       * Append repeat the sub-activity, i.e.,
       * repeat_P1 -> P2
       * or
       * repeat_P2 -> ACT
       */
      if (i + 1 < ancestors.size)
        this.append("repeat_$name -> ${ancestors[i + 1]}")
      else
        this.append("repeat_$name -> ACT")

      /*
       * Append end an activity, if the activity is the root, it should be reset after end, i.e.,
       * end_P2 -> END_REPEAT_P1
       * or
       * end_P1 -> reset_P1 -> A
       */
      if (i > 0) {
        this.append(" | end_$name -> END_REPEAT_${ancestors[i - 1]}")
        this.append("),\n")
      } else {
        this.append(" | end_$name -> reset_$name -> $actName")
        this.append(")")
        // Append relabel command if the action is renamed.
        if (action.humanAction !in relabels)
          this.append(".\n\n")
        else
          this.append("/{${relabels[action.humanAction]}/${action.humanAction}}.\n\n")
      }
    }

    helper(0)
    return actName
  }

  /**
   *
   */
  private fun StringBuilder.appendOperator(operator: String, subNames: List<String>, parent: String): String {
    // Name of this operator process
    val name = operator.toUpperCase() + "_" + subNames.joinToString("_")
    // The code snippet for skipping sub-activities
    val skips = subNames.joinToString(", ") { "skip_$it" }

    this.append("$name = (")
    when (operator) {
      /*
       * For activity tree, A -(ord)-> (B, C), it will be translated to the following process:
       * ORD_B_C = (start_B -> end_B -> C | end_B -> C),
       * C = (start_C -> end_C -> ORD_B_C | end_C -> ORD_B_C)+{skip_B, skip_C}.
       */
      "ord" -> {
        for (i in subNames.indices) {
          if (i != subNames.size - 1) {
            this.append("start_${subNames[i]} -> end_${subNames[i]} -> ${subNames[i + 1]}")
            this.append(" | ")
            this.append("end_${subNames[i]} -> ${subNames[i + 1]}")
            this.append("),\n")
            this.append("${subNames[i + 1]} = (")
          } else {
            this.append("start_${subNames[i]} -> end_${subNames[i]} -> $name")
            this.append(" | ")
            this.append("end_${subNames[i]} -> $name")
            this.append(")+{$skips}.\n\n")
          }
        }
      }
      /*
       * For activity tree, A -(and_seq)-> (B, C), it will be translated to the following process:
       * AND_SEQ_B_C = (
       *      start_B -> end_B -> AND_SEQ_B_C | end_B -> AND_SEQ_B_C
       *    | start_C -> end_C -> AND_SEQ_B_C | end_C -> AND_SEQ_B_C
       * )+{skip_B, skip_C}.
       */
      "and_seq" -> {
        var tab = "\t\t"
        this.append('\n')
        for (it in subNames) {
          this.append(tab)
          tab = "\t|\t"
          this.append("start_$it -> end_$it -> $name | end_$it -> $name\n")
        }
        this.append(")+{$skips}.\n\n")
      }
      /*
       * For activity tree, A -(and_par)-> (B, C), it will be translated to the following process:
       * AND_PAR_B_C = END+{skip_B, skip_C}.
       */
      "and_par" -> {
        this.setLength(this.length - 1)
        this.append("END+{$skips}.\n\n")
      }
      /*
       * For activity tree, A -(or_seq)-> (B, C), it will be translated to the following process:
       * OR_SEQ_B_C = (
       *      start_B -> end_B -> SKIP | end_B -> SKIP
       *    | start_C -> end_C -> SKIP | end_C -> SKIP
       *    | end_A -> OR_SEQ_B_C
       * ),
       * SKIP = (
       *      start_B -> end_B -> SKIP | end_B -> SKIP | skip_B -> SKIP
       *    | start_C -> end_C -> SKIP | end_C -> SKIP | skip_C -> SKIP
       *    | repeat_A -> OR_SEQ_B_C | end_A -> OR_SEQ_B_C
       * ).
       */
      "or_seq" -> {
        var tab = "\t\t"
        this.append('\n')
        for (it in subNames) {
          this.append(tab)
          tab = "\t|\t"
          this.append("start_$it -> end_$it -> SKIP | end_$it -> SKIP\n")
        }
        this.append(tab)
        this.append("end_$parent -> $name")
        this.append("\n),\n")
        this.append("SKIP = (\n")
        tab = "\t\t"
        for (it in subNames) {
          this.append(tab)
          tab = "\t|\t"
          this.append("start_$it -> end_$it -> SKIP | end_$it -> SKIP | skip_$it -> SKIP\n")
        }
        this.append(tab)
        this.append("repeat_$parent -> $name | end_$parent -> $name")
        this.append("\n).\n\n")
      }
      /*
       * For activity tree, A -(or_par)-> (B, C), it will be translated to the following process:
       * OR_PAR_B_C = (
       *      start_B -> SKIP | end_B -> SKIP
       *    | start_C -> SKIP | end_C -> SKIP
       *    | end_A -> OR_PAR_B_C
       * ),
       * SKIP = (
       *      start_B -> SKIP | end_B -> SKIP | skip_B -> SKIP
       *    | start_C -> SKIP | end_C -> SKIP | skip_C -> SKIP
       *    | repeat_A -> OR_PAR_B_C | end_A -> OR_PAR_B_C
       * ).
       */
      "or_par" -> {
        var tab = "\t\t"
        this.append('\n')
        for (it in subNames) {
          this.append(tab)
          tab = "\t|\t"
          this.append("start_$it -> SKIP | end_$it -> SKIP\n")
        }
        this.append(tab)
        this.append("end_$parent -> $name")
        this.append("\n),\n")
        this.append("SKIP = (\n")
        tab = "\t\t"
        for (it in subNames) {
          this.append(tab)
          tab = "\t|\t"
          this.append("start_$it -> SKIP | end_$it -> SKIP | skip_$it -> SKIP\n")
        }
        this.append(tab)
        this.append("repeat_$parent -> $name | end_$parent -> $name")
        this.append("\n).\n\n")
      }
      /*
       * For activity tree, A -(optor_seq)-> (B, C), it will be translated to the following process:
       * OPTOR_SEQ_B_C = (
       *      start_B -> end_B -> OPTOR_SEQ_B_C | end_B -> OPTOR_SEQ_B_C | skip_B -> OPTOR_SEQ_B_C
       *    | start_C -> end_C -> OPTOR_SEQ_B_C | end_C -> OPTOR_SEQ_B_C | skip_C -> OPTOR_SEQ_B_C
       * ).
       */
      "optor_seq" -> {
        var tab = "\t\t"
        this.append('\n')

        for (it in subNames) {
          this.append(tab)
          tab = "\t|\t"
          this.append("start_$it -> end_$it -> $name | ")
          this.append("end_$it -> $name | ")
          this.append("skip_$it -> $name\n")
        }
        this.append(").\n\n")
      }
      /*
       * For activity tree, A -(optor_par)-> (B, C), it will be translated to the following process:
       * OPTOR_PAR_B_C = END.
       */
      "optor_par" -> {
        this.setLength(this.length - 1)
        this.append("END.\n\n")
      }
      /*
       * For activity tree, A -(xor)-> (B, C), it will be translated to the following process:
       * XOR_B_C = (
       *      start_B -> end_B -> SKIP | end_B -> SKIP
       *    | start_C -> end_C -> SKIP | end_C -> SKIP
       *    | end_A -> XOR_B_C
       * ),
       * SKIP = (skip_B -> SKIP | skip_C -> SKIP | repeat_A -> XOR_B_C | end_A -> XOR_B_C).
       */
      "xor" -> {
        var tab = "\t\t"
        this.append('\n')
        for (it in subNames) {
          this.append(tab)
          tab = "\t|\t"
          this.append("start_$it -> end_$it -> SKIP | end_$it -> SKIP\n")
        }
        this.append(tab)
        this.append("end_$parent -> $name")
        this.append("\n),\n")
        this.append("SKIP = (")
        this.append(subNames.joinToString(" | ") { "skip_$it -> SKIP" })
        this.append(" | ")
        this.append("repeat_$parent -> $name | end_$parent -> $name")
        this.append(").\n\n")
      }
      else -> throw IllegalArgumentException("$operator is not supported")
    }

    return name
  }

  /**
   * @return The name of LTSA process.
   */
  private fun StringBuilder.appendCondition(
      name: String, preConditions: List<String>,
      repeatConditions: List<String>, completionConditions: List<String>
  ): String {
    val condName = name + "_COND"
    var tab = "\t\t"
    val variables = inputVariables.joinToString("") { "[${it.name}]" }

    /*
     * Append the process name and its initial value.
     * A_COND = VAR[<initial values>],
     */
    this.append("$condName = VAR")
    this.append(inputVariables.joinToString("") { "[${it.initialValue}]" })
    this.append(",\n")
    /*
     * VAR[<variables>] = (
     *      when (precondition && !completioncondition)
     *        start_A -> VAR[<variables>]
     *    | when (!(precondition && !completioncondition))
     *        start_A -> commission_A -> VAR[<variables>]
     *    | when (repeatcondition && !(completioncondition))
     *        repeat_A -> VAR[<vairables>]
     *    | when (!(repeatcondition && !(completioncondition)))
     *        repeat_A -> repetition_A -> VAR[<vairables>]
     *    | when (completioncondition)
     *        end_A -> VAR[<variables>]
     *    | when (!completioncondition)
     *        end_A -> omission_A -> VAR[<variables>]
     *    | <variable changes in world model>
     * ),
     */
    this.append("VAR")
    this.append(inputVariables.joinToString("") { "[${it.name}:${it.userDefinedType}]" })
    this.append(" = (\n")

    // Append preconditions
    if (preConditions.isNotEmpty() || completionConditions.isNotEmpty()) {
      this.append(tab); tab = "\t|\t"

      val pres = preConditions.joinToString(" && ")
      val completions = completionConditions.joinToString(" && ")
      val cond = if (pres != "" && completions != "")
        "$pres && !($completions)"
      else if (pres != "")
        pres
      else
        "!($completions)"
      this.append("when ($cond) ")
      this.append("start_$name -> VAR$variables\n")

      // !!!IMPORTANT: Append commission error
      if (withError) {
        this.append(tab); tab = "\t|\t"
        this.append("when (!($cond)) ")
        this.append("start_$name -> commission_$name -> VAR$variables\n")
      }
    } else {
      this.append(tab); tab = "\t|\t"
      this.append("start_$name -> VAR$variables\n")
    }

    // Append repetition conditions
    if (repeatConditions.isNotEmpty() || completionConditions.isNotEmpty()) {
      this.append(tab); tab = "\t|\t"

      val repeats = repeatConditions.joinToString(" && ")
      val completions = completionConditions.joinToString(" && ")
      val cond = if (repeats != "" && completions != "")
        "$repeats && !($completions)"
      else if (repeats != "")
        repeats
      else
        "!($completions)"
      this.append("when ($cond) ")
      this.append("repeat_$name -> VAR$variables\n")

      // !!!IMPORTANT: Append repetition error
      if (withError) {
        this.append(tab); tab = "\t|\t"
        this.append("when (!($cond)) ")
        this.append("repeat_$name -> repetition_$name -> VAR$variables\n")
      }
    } else {
      this.append(tab); tab = "\t|\t"
      this.append("repeat_$name -> VAR$variables\n")
    }

    // Append completion condition
    if (completionConditions.isNotEmpty()) {
      this.append(tab); tab = "\t|\t"

      val cond = completionConditions.joinToString(" && ")
      this.append("when ($cond) ")
      this.append("end_$name -> VAR$variables\n")

      // !!!IMPORTANT: Append omission error!!!
      if (withError) {
        this.append(tab); tab = "\t|\t"
        this.append("when (!($cond)) ")
        this.append("end_$name -> omission_$name -> VAR$variables\n")
      }
    } else {
      this.append(tab); tab = "\t|\t"
      this.append("end_$name -> VAR$variables\n")
    }
    // Append variables change
    for (l in world) {
      this.append(tab); tab = "\t|\t"
      this.append(l)
      this.append('\n')
    }
    this.append(").\n\n")

    return condName
  }
}
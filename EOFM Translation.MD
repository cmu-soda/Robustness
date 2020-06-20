# EOFM to LTSA Translation
This file describe the translation process from EOFM XML model to LTSA model. The translation process takes ideas from Matthew Bolton's paper,

M. L. Bolton, R. I. Siminiceanu, and E. J. Bass, “A systematic approach to model checking human-automation interaction using task analytic models,” IEEE Trans. Syst. Man, Cybern. Part ASystems Humans, vol. 41, no. 5, pp. 961–976, 2011.

## Constants and User defined types
The constants in EOFM models are translated into constants in FSP by using the ```const``` keyword. However, in FSP, the constants could only be integers.

The user defined types in EOFM models are translated into ranges by using the ```range``` keyword. Each element in a user defined type is translated into a constant in FSP by using the ```const``` keyword.

### Example:

EOFM:
```
<userdefinedtype name="TBool">{False, True}</userdefinedtype>
```

FSP:
```
const False = 0
const True = 1
range TBool = False..True
```

## Input variables
Input variables are only used in translating the conditions for activities. Right now, we only support input variables in integers or user defined types (which are translated into integers).

## Human Actions
Human actions become actions (events) in the FSP model.

TODO: human action has a 'behavior' attribute in EOFM. Right now, we only find the 'autoreset' value for it. What are the other possible values and how they should behave is unclear.

## Actions
Each action should represent an action/event in the FSP model. However, we still need a way to build the hierarchical structure in EOFM and also the operators which control the execution sequences of the actions/sub-activities. Therefore, in our translation, each action is translated into a process with some helper events to control its execution order. Through out the following sections, we will use a simple EOFM model to illustrate the idea:
```
<eofms>
    <userdefinedtype name="TBool">{False, True}</userdefinedtype>
    <humanoperator name="User">
        <inputvariable name="iX" userdefinedtype="TBool"/>
        <humanaction name="hA" behavior="autoreset"/>
        <humanaction name="hB" behavior="autoreset"/>

        <eofm>
            <activity name="aA">
                <precondition>iX == False</precondition>
                <completioncondition>iX == True</completioncondition>
                <decomposition operator="${we will use different operator here}">
                    <activity name="aB">
                        <decomposition operator="ord">
                            <action humanaction="hA"/>
                        </decomposition>
                    </activity>
                    <activity name="aC">
                        <decomposition operator="ord">
                            <action humanaction="hB"/>
                        </decomposition>
                    </activity>
                </decomposition>
            </activity>
        </eofm>
    </humanoperator>
</eofms>
```

The above EOFM model has the following hierarchy:
```
     aA
  |------|
  aB     aC
  |      |
  hA     hB
```
where ```aA``` is the top level activity; ```aB``` and ```aC``` are the two sub-activities; ```hA``` and ```hB``` are two actions.

For the ```hA``` action in this hierarchy, we translate it into a FSP process:
```
HA = (start_AA -> AB | end_AA -> reset_AA -> HA),
AB = (start_AB -> ACT | end_AB -> END_REPEAT_AA | skip_AB -> END_REPEAT_AA),
ACT = (hA -> END_REPEAT_AB),
END_REPEAT_AB = (repeat_AB -> ACT | end_AB -> END_REPEAT_AA),
END_REPEAT_AA = (repeat_AA -> AB | end_AA -> reset_AA -> HA).
```
```HA``` is the name of the process which derives from the name of the action ```hA```. ```start_AA``` and ```start_AB``` are the helper events corresponding to the ancestor activities ```aA``` and ```aB``` of this action respectively, used to control the start condition. Similarly, ```end_AA, end_AB, skip_AB, repeat_AB, repeat_AA, reset_AA,``` are also helper events used to control the execution order defined by the operators. We only have ```reset_X``` event for the root activity because the reset of the sub-activities and sub-sub-activities are controlled by the parent repeat or root reset. We only have the ```skip_X``` events for the sub-activities because we cannot skip a root activity. The ```skip``` events are used for the *or* operators. The use of these helper events will be discussed in the next section.

## Operators
An operator is also translated into a process in FSP. The operator process acts like a lock or a mutex in order to control the execution sequence of the sub-activities or actions. We support all the operators in EOFM except the ```sync``` operator, since it is impossible in LTSA to have multiple actions happening at exact the same time unless we combine them into one action.

**Also, readers can play with the test case, test/java/edu.cmu.isr.robust/eofm/TranslatorTest.testOperators, which prints the code for the following examples.**

### ord
```
ORD_AB_AC = (start_AB -> end_AB -> AC | end_AB -> AC),
AC = (start_AC -> end_AC -> ORD_AB_AC | end_AC -> ORD_AB_AC)+{skip_AB, skip_AC}.
```
The process defines that we can only choose to ```start``` activity ```aB``` first or directly ```end``` it if it is possible. After that, we can then ```start``` or directly ```end``` the ```aC``` activity. Also, we add ```skip_AB, skip_AC``` to the alphabet so that skips cannot happen after composition, which means we should not skip any activities.

### and_seq
```
AND_SEQ_AB_AC = (
		start_AB -> end_AB -> AND_SEQ_AB_AC | end_AB -> AND_SEQ_AB_AC
	|	start_AC -> end_AC -> AND_SEQ_AB_AC | end_AC -> AND_SEQ_AB_AC
)+{skip_AB, skip_AC}.
```
It is mostly similar to ```ord``` translation. The only difference is that we can do either activity ```aB``` or ```aC``` first. And if we choose to do an activity, e.g., ```aB```, we have to wait for its completion ```end_AB``` before we can do the next activity.

### and_par
```
AND_PAR_AB_AC = END+{skip_AB, skip_AC}.
```
In ```and_par```, we can execution the sub-activities in any order and they can execute simultaneously. Thus, we have no constraints on the order. However, we add the skip events to the alphabet so that no activities can be skipped.

### or_seq
```
OR_SEQ_AB_AC = (
		start_AB -> end_AB -> SKIP | end_AB -> SKIP
	|	start_AC -> end_AC -> SKIP | end_AC -> SKIP
	|	end_AA  -> OR_SEQ_AB_AC
),
SKIP = (
		start_AB -> end_AB -> SKIP | end_AB -> SKIP | skip_AB -> SKIP
	|	start_AC -> end_AC -> SKIP | end_AC -> SKIP | skip_AC -> SKIP
	|	repeat_AA -> OR_SEQ_AB_AC | end_AA -> OR_SEQ_AB_AC
).
```
In ```or_seq```, we can choose to do either activity ```aB``` or ```aC``` first. But after that, we have to wait for the completion of that activity. After any of the activities has completed, we enter the SKIP process where we can choose to skip the reset activities.

### or_par
```
OR_PAR_AB_AC = (
		start_AB -> SKIP | end_AB -> SKIP
	|	start_AC -> SKIP | end_AC -> SKIP
	|	end_AA -> OR_PAR_AB_AC
),
SKIP = (
		start_AB -> SKIP | end_AB -> SKIP | skip_AB -> SKIP
	|	start_AC -> SKIP | end_AC -> SKIP | skip_AC -> SKIP
	|	repeat_AA -> OR_PAR_AB_AC | end_AA -> OR_PAR_AB_AC
).
```
```or_par``` is very similar to the ```or_seq``` translation. The only difference is that after a ```start_X``` event, we do not have to wait on its corresponding completion ```end_X```.

### optor_seq
```
OPTOR_SEQ_AB_AC = (
		start_AB -> end_AB -> OPTOR_SEQ_AB_AC | end_AB -> OPTOR_SEQ_AB_AC | skip_AB -> OPTOR_SEQ_AB_AC
	|	start_AC -> end_AC -> OPTOR_SEQ_AB_AC | end_AC -> OPTOR_SEQ_AB_AC | skip_AC -> OPTOR_SEQ_AB_AC
).
```
```optor_seq``` is similar to ```or_seq```. However, we do not need the additional SKIP sub-process, because we can choose to do nothing. It means that we can skip actions at the beginning.

### optor_par
```
OPTOR_PAR_AB_AC = END.
```
```optor_par``` is the most flexible operator where we can execute activities in any order simultaneously, and also skip activities at any time.

### xor
```
XOR_AB_AC = (
		start_AB -> end_AB -> SKIP | end_AB -> SKIP
	|	start_AC -> end_AC -> SKIP | end_AC -> SKIP
	|	end_AA -> XOR_AB_AC
),
SKIP = (skip_AB -> SKIP | skip_AC -> SKIP | repeat_AA -> XOR_AB_AC | end_AA -> XOR_AB_AC).
```
In ```xor```, it behaves like ```or_seq``` at the beginning, we can choose to do either activity ```aB``` or ```aC``` and have to wait for its completion. After that, we enter a special SKIP sub-process where we can only skip the reset activities.

## Conditions
In the Operators section, we use help actions such as ```start_X, end_X, repeat_X, skip_X``` to control the execution order of activities. They correspond to the *startcondition*, *endcondition*, and *resetcondition* described in Bolton's paper. This section describes the translation process for another important concept in EOFM, the activity conditions, including *precondition*, *completioncondition*, and *repeatcondition*.

The conditions associated to an activity are translated into one process in FSP. It should follow the following pattern:
```
<activity name>_COND = VAR[<initial values>],
VAR[<variables>] = (
     when (precondition && !completioncondition)
       start_<activity name> -> VAR[<variables>]
   | when (repeatcondition && !(completioncondition))
       repeat_<activity name> -> VAR[<vairables>]
   | when (completioncondition)
       end_<activity name> -> VAR[<variables>]
   | <variable changes in world model>
)
```
```VAR[<variables>]``` defines a sub-process where it adds guards to the ```start_X, repeat_X, end_X``` helper events based on the *precondition*, *completioncondition*, and *repeatcondition* associated to the activity. For example, according to Bolton's paper, an activity can start when its precondition is satisfied and its completioncondition is not satisfied. Thus, we add a guard function, ```when (precondition && !completioncondition)```, to the ```start_X``` event.

In the example we described above, we have:
```
<activity name="aA">
    <precondition>iX == False</precondition>
    <completioncondition>iX == True</completioncondition>
    ...
</activity>
```
It will be translated into a process:
```
AA_COND = VAR[False],
VAR[iX:TBool] = (
    when (iX == False && !(iX == True))
      start_AA -> VAR[iX]
  |	when (!(iX == True))
      repeat_AA -> VAR[iX]
  |	when (iX == True)
      end_AA -> VAR[iX]
  | <variable changes in world model>
).
```

The ```<variables>``` placeholder refers to **ALL** the input variables defined in the EOFM model. The reason why ALL the variables are needed will be explained later, as well as the ```<variable changes in world model>``` placeholder.

## Activities
An activity is the parallel composition of all its sub-activities or actions. In our example, we have:

### Activity aB
```
||AB = (HA).
```

### Activity aC
```
||AC = (HB).
```

### Activity aA
```
||AA = (AB || AC || <operator>_AB_AC || AA_COND).
```

## World Model
The conditions of an activity defines that when this activity can be executed, repeat, and end. The conditions are defined based on the input variables which are, in fact, the states of the system which the human operator interacts with. The system states will change according to actions from the operator. However, in FSP, we do not explicitly model system states. Therefore, we have to manually create this event-state-changing relation in the translated EOFM model; otherwise, we will have a human behavior model which repeats the first action forever because the system does not respond to that action. We call such additional information the *world model* which basically defines how the input variables (system states) will change when an action happens.

Back to our example, let's assume that we have a system spec as below:
```
SYS = (hA -> hB -> restart | hB -> hA -> restart).
```
We do not explicitly model the state variable ```iX``` in this model. However, we know that ```iX == False``` in the initial state; after ```hA -> hB``` or ```hB -> hA```, ```iX``` becomes ```True```; finally, the ```restart``` event (an internal event of the system) will reset ```iX``` to ```False```. Thus, we have to explicitly model these transitions into the translated EOFM model, in particular, into the condition processes where we use input variables to define guard functions. We have world model:
```
"when (iX == False) hA -> hB -> VAR[True]",
"when (iX == False) hB -> hA -> VAR[True]",
"when (iX == True) restart -> VAR[False]"
```
Then, we append these transitions to all the condition processes. In our example, we only have one condition process, i.e., ```AA_COND```. Thus, we have
```
AA_COND = VAR[False],
VAR[iX:TBool] = (
		when (iX == False && !(iX == True)) start_AA -> VAR[iX]
	|	when (!(iX == True)) repeat_AA -> VAR[iX]
	|	when (iX == True) end_AA -> VAR[iX]
	|	when (iX == False) hA -> hB -> VAR[True]
	|	when (iX == False) hB -> hA -> VAR[True]
	|	when (iX == True) restart -> VAR[False]
).
```

## Initial Values
EOFM does not provide the facilities to model initial values of input variables. However, in our translation, the initial values are required; otherwise, the tool does not know how to build the state machines. The constructor of the Translator class takes a map named ```initValues``` to set the initial values for all the input variables. In our example, we have ```mapOf("iX" to "False")```.

## Relabels
FSP provides the function to relabel the names of events. This is useful when developers want to synchronize events in different processes with different names. We also provide this functionality for the translation process. The Translator class takes a map named ```relabels``` as input, and the translation process relabels all the names in the key to the corresponding names in the value.

## Erroneous Human Behaviors
The translation process can also inject erroneous human behaviors to the translated model. The error injection process is based on Matthew Bolton's paper,

M. L. Bolton and E. J. Bass, “Generating erroneous human behavior from strategic knowledge in task models and evaluating its impact on system safety with model checking,” IEEE Trans. Syst. Man, Cybern. Syst., vol. 43, no. 6, pp. 1314–1327, 2013.

The general idea of the paper is that: human operators should execute, repeat, or end an activity or an action only when the corresponding condition is met. For instance, an operator should only start an action when its precondition is met. These conditions are considered as the strategic knowledge of a human operator, and an operator may mistakenly execute or end an action by violating these conditions because of some cognitive errors. Therefore, Bolton formalizes these cognitive errors (genotype errors) by using the different conditions in the EOFM model. For example, an omission error can be represented by the human operator ends an activity without the satisfaction of its completion condition.

In our translation, this become straightforward since we have the condition process for each activity. We change the condition process pattern to:
```
<activity name>_COND = VAR[<initial values>],
VAR[<variables>] = (
     when (precondition && !completioncondition)
       start_<activity name> -> VAR[<variables>]
   | when (!(precondition && !completioncondition))
       start_<activity name> -> commission_<activity name> -> VAR[<variables>]
   | when (repeatcondition && !(completioncondition))
       repeat_<activity name> -> VAR[<vairables>]
   | when (!(repeatcondition && !(completioncondition)))
       repeat_<activity name> -> repetition_<activity name> -> VAR[<vairables>]
   | when (completioncondition)
       end_<activity name> -> VAR[<variables>]
   | when (!completioncondition)
       end_<activity name> -> omission_<activity name> -> VAR[<variables>]
   | <variable changes in world model>
)
```
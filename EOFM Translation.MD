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
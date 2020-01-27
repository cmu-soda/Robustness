## Build instruction

### Import project to IntelliJ IDEA
Import Project -> Select 'LTSA-Robust/robustness-calculator' -> Import project from external model 'Maven' -> Finish

### Add ltsa.jar to project path
File -> Project Structure -> Libraries -> Select '+' and Java -> Select the 'ltsa.jar' -> OK

### Build project should world now!

## Try it out!

### Test: translation of coffee user EOFM model
1. Go to 'test/java/TranslatorTest' -> Right click on ```fun testCoffeeEOFM``` -> Select 'Run testCoffeeEOFM'

2. This should print the translated LTSA model of the coffee EOFM model to screen.

### Test: generate human behavior model when composing with the system spec
0. 'main/resources/specs/coffee_eofm/generate_human.lts' - This file is the combination of the translated coffee user EOFM model (human_eofm.lts) and the coffee machine system model (machine.lts).

1. Go to 'test/java/ManualTest' -> Right click on ```fun genCoffeeHumanTest()``` -> Select 'Run genCoffeeHumanTest'

2. This should print a concise human behavior model after applying the EOFM model to the system.

3. WHY? Since EOFM is a more general model, after performing an action, the model assumes that the operator should observe the current system state and then decide the next action. For example, the coffee EOFM model defines: ```hLiftHandle -> if handle is up, then do next action; otherwise, repeat hLiftHandle```. However, from the system model, we know that ```hLiftHandle``` will cause the handle up. Thus, we can simply represent it as ```hLifhtHandle -> next action```.

### Test: generate weakest assumption and extract deviation traces
1. Go to 'test/java/RobustCalTest' -> Right click on ```fun testCoffeeEOFM``` -> Select 'Run testCoffeeEOFM'

2. This should print the LTSA spec of the weakest assumption and the model to generate deviation traces.

3. Also, processes named ```TRACE``` are the deviation traces which are allowed by the coffee machine but not defined in the human behavior model.
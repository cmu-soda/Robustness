import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import *

alphabet = ["back", "confirm", "password", "select", "vote",
            "eo.enter", "eo.exit", "v.enter", "v.exit"]


r = Repair(
    sys=["sys.lts"],
    env_p=["env.lts"],
    safety=["p2.lts"],
    desired={   # rank the desired behavior by importance
        PRIORITY3: ["confirm.fsm"],
        PRIORITY2: [],
        PRIORITY1: [],  
        PRIORITY0: []
    },
    alphabet=alphabet,  # \alpha M \union \alpha E
    controllable={  # rank the controllable events by cost
        PRIORITY3: ["eo.enter", "eo.exit", "v.enter", "v.exit"],
        PRIORITY2: [],
        PRIORITY1: ["back", "confirm", "password", "select", "vote"], 
        PRIORITY0: []
    },
    observable={    # rank observable events by cost
        PRIORITY3: [],
        PRIORITY2: ["eo.enter", "eo.exit", "v.enter", "v.exit"],
        PRIORITY1: ["back", "confirm", "password", "select", "vote"], 
        PRIORITY0: []
    }
)

weight_dict, weight_list = r.computeWeights()
print(weight_dict)
print(weight_list)
utility = r.computeCost(["confirm.fsm"], ["back", "confirm", "password", "select", "vote"], [], weight_dict)
print(utility)
result = r.synthesize(5)
print(result)

#weight_dict, weight_list = r.computeWeights()
#subsets, troll = r.computeDesiredSubsets(6, weight_list)
#print(subsets)
#result = r.synthesize(6)
#print(result)

# for s in r.synthesize(n=3): # generate maximum 3 solutions
#     print(s)
#controllable = alphabet
#observable = alphabet
#C, plant, _ = r._synthesize([], controllable, observable)
#r.minimize_controller(plant, C, controllable, observable)

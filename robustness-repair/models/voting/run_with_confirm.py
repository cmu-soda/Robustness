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
    preferred={   # rank the preferred behavior by importance
        PRIORITY3: ["confirm.lts", "vote.lts"],
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

# weight_dict, weight_list = r.compute_weights()
# print(weight_dict)
# print(weight_list)
# utility = r.compute_cost(["confirm.lts"], ["back", "confirm", "password", "select", "vote"], [], weight_dict)
# print(utility)
# result = r.synthesize(5)
# print(result)

#weight_dict, weight_list = r.computeWeights()
#subsets, troll = r.computepreferredSubsets(6, weight_list)
#print(subsets)
#result = r.synthesize(6)
#print(result)

# for s in r.synthesize(n=3): # generate maximum 3 solutions
#     print(s)
controllable = alphabet
observable = alphabet
C, plant, _ = r._synthesize(controllable, observable)
sup, min_controllable, min_observable = r.remove_unnecessary(plant, C, controllable, observable)
print(r.check_preferred(sup, min_controllable, min_observable, ["confirm.lts", "vote.lts"]))

controllable = ["back", "confirm", "password", "select", "vote"]
observable = ["back", "confirm", "password", "select", "vote"]
C, plant, _ = r._synthesize(controllable, observable)
sup, min_controllable, min_observable = r.remove_unnecessary(plant, C, controllable, observable)
print(r.check_preferred(sup, min_controllable, min_observable, ["confirm.lts", "vote.lts"]))

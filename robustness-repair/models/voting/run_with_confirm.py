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
        HIGH: ["confirm.fsm"],
        MEDIUM: [],
        LOW: [] 
    },
    alphabet=alphabet,  # \alpha M \union \alpha E
    controllable={  # rank the controllable events by cost
        HIGH: ["eo.enter", "eo.exit", "v.enter", "v.exit"],
        MEDIUM: [],
        LOW: ["back", "confirm", "password", "select", "vote"]
    },
    observable={    # rank observable events by cost
        HIGH: [],
        MEDIUM: ["eo.enter", "eo.exit", "v.enter", "v.exit"],
        LOW: ["back", "confirm", "password", "select", "vote"]
    }
)
for s in r.synthesize(n=3): # generate maximum 3 solutions
    print(s)

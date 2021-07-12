import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import Repair, HIGH, MEDIUM, LOW

alphabet = ["back", "confirm", "password", "select", "vote",
            "eo.enter", "eo.exit", "v.enter", "v.exit"]

r = Repair(
    plant=["sys.lts", "env.lts"],
    safety=["p2.lts"],
    desired={   # rank the desired behavior by importance
        HIGH: ["confirm.fsm"],
        MEDIUM: [],
        LOW: [] 
    },
    alphabet=alphabet,
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

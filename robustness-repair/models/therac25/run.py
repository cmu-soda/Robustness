import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import *

alphabet = set([
    "hPressX", "hPressE", "hPressEnter", "hPressB", "mFire", "hPressUp", "hPressUp1", "mEBeamLvl", "mXrayLvl",
    "mInPlace", "mOutPlace", "mInitXray", "mInitEBeam"
])
r = Repair(
    sys= ["interface.lts", "power.lts"],
    env_p = ["env.lts"], 
    safety =["p.lts"],
    preferred={   # rank the preferred behavior by importance
        PRIORITY3: ["back.lts", "fire.lts"],
        PRIORITY2: ["back1.lts"],
        PRIORITY1: [],  
        PRIORITY0: []
    },
    progress=[],
    alphabet=alphabet,
    controllable={  # rank the controllable events by cost
        PRIORITY3: ["hPressX", "hPressE", "hPressEnter", "hPressB"],
        PRIORITY2: ["hPressUp", "hPressUp1"],
        PRIORITY1: ["mFire", "mEBeamLvl", "mXrayLvl","mInPlace", "mOutPlace", "mInitXray","mInitEBeam"],
        PRIORITY0: []
    },
    observable={    # rank observable events by cost
        PRIORITY3: [],
        PRIORITY2:  ["hPressX", "hPressE", "hPressEnter", "hPressB"],
        PRIORITY1: ["hPressUp", "hPressUp1"],
        PRIORITY0: ["mFire", "mEBeamLvl", "mXrayLvl","mInPlace", "mOutPlace", "mInitXray","mInitEBeam"] 
    }
)


result = r.synthesize(6)
for c in result:
    print(r.fsm2fsp(c["M_prime"], c["observable"], name="M"))

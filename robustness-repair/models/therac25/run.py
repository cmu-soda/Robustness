import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import Repair

alphabet = set([
    "hPressX", "hPressE", "hPressEnter", "hPressB", "mFire", "hPressUp", "hPressUp1", "mEBeamLvl", "mXrayLvl",
    "mInPlace", "mOutPlace", "mInitXray", "mInitEBeam"
])
r = Repair(
    plant=["interface.lts", "power.lts", "env.lts"],
    property=["p.lts"],
    alphabet=alphabet,
    controllable=set(["hPressX", "hPressE", "hPressEnter", "hPressB", "hPressUp", "hPressUp1"]),
    observable=alphabet
)
r.synthesize()

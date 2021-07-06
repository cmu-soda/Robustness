import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import Repair

alphabet = set(["back", "confirm", "password", "select", "vote",
                "eo.enter", "eo.exit", "v.enter", "v.exit"])

r = Repair(
    plant=[Repair.abstract("sys.lts", ["password"]), "env.lts"],
    property = ["p2.lts"], 
    alphabet = alphabet,
    controllable = set(["back", "confirm", "password", "select", "vote"]),
    observable = alphabet-set(["eo.enter", "eo.exit", "v.enter", "v.exit"])
)
r.synthesize()

import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import Repair

alphabet = set(["back", "confirm", "password", "select", "vote",
                "eo.enter", "eo.exit", "v.enter", "v.exit"])

r = Repair(
    plant=["sys.lts", "env.lts"],
    property = ["p.lts"], 
    alphabet = alphabet,
    controllable = set(["back", "confirm", "password", "select", "vote"]),
    observable = alphabet
)
r.synthesize()

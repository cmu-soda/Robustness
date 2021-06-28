import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import Repair

alphabet = set(["send", "rec", "ack", "getack", "input", "output"])
r = Repair(
    plant=["channel.lts", "receiver.lts", "sender.lts"],
    property=["p.lts"],
    alphabet=alphabet,
    controllable=alphabet-set(["input", "output"]),
    observable=alphabet
)
r.synthesize()

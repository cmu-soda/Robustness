import subprocess
import os
from os import path
import sys
import DESops as d
from lts import StateMachine


class Repair:
    def __init__(self, plant, property, controllable, observable):
        self.plant = plant
        self.property = property
        self.controllable = controllable
        self.observable = observable
    
    def synthesize(self):
        if not path.exists("tmp"):
            os.mkdir("tmp")
        plant = list(map(lambda x: self.lts2fsm(x), self.plant))
        plant = plant[0] if len(plant) == 1 else d.composition.parallel(*plant)
        p = list(map(lambda x: self.lts2fsm(x), self.property))
        p = p[0] if len(p) == 1 else d.composition.parallel(*p)

        L = d.supervisor.supremal_sublanguage(plant, p, prefix_closed=True, mode=d.supervisor.Mode.CONTROLLABLE_NORMAL)
        L = d.composition.observer(L)
        if len(L.vs) != 0:
            self.fsm2lts(L, "sup")
        else:
            print("Cannot find a controller")


    def lts2fsm(self, file):
        print("Convert", file, "to fsm model")
        name = path.basename(file)
        tmp_json = f"tmp/{name}.json"
        with open(tmp_json, "w") as f:
            subprocess.run([
                "java",
                "-cp",
                "../bin/robustness-calculator.jar",
                "edu.cmu.isr.robust.ltsa.LTSAHelperKt",
                "--lts",
                file,
            ], stdout=f)
        m = StateMachine.from_json(tmp_json)
        tmp_fsm = f"tmp/{name}.fsm"
        m.to_fsm(self.controllable, self.observable, tmp_fsm)
        return d.read_fsm(tmp_fsm)

    def fsm2lts(self, fsm, name):
        fsm_file = f"tmp/{name}.fsm"
        d.write_fsm(fsm_file, fsm)
        alphabet = list(set(self.controllable).union(set(self.observable)))
        m = StateMachine.from_fsm(fsm_file, alphabet)
        json_file = f"tmp/{name}.json"
        m.to_json(json_file)
        lts = subprocess.check_output([
            "java",
            "-cp",
            "../bin/robustness-calculator.jar",
            "edu.cmu.isr.robust.ltsa.LTSAHelperKt",
            "--json",
            json_file,
        ], text=True)
        print("=============================")
        print("Synthesized Controller:")
        print(lts)


# r = Repair(
#     plant=["models/therac25/interface.lts", "models/therac25/power.lts", "models/therac25/env.lts"],
#     property=["models/therac25/p.lts"],
#     controllable=["hPressX", "hPressE", "hPressEnter", "hPressB", "mFire", "hPressUp", "hPressUp1", "mEBeamLvl", "mXrayLvl",
#         "mInPlace", "mOutPlace", "mInitXray", "mInitEBeam"],
#     observable=["hPressX", "hPressE", "hPressEnter", "hPressB", "mFire", "hPressUp", "hPressUp1", "mEBeamLvl", "mXrayLvl",
#         "mInPlace", "mOutPlace", "mInitXray", "mInitEBeam"]
# )
# r.synthesize()

r = Repair(
    plant=["models/abp/channel.lts", "models/abp/receiver.lts", "models/abp/sender.lts"],
    property=["models/abp/p.lts"],
    controllable=["send", "rec", "ack", "getack"],
    observable=["send", "rec", "ack", "getack", "input", "output"]
)
r.synthesize()

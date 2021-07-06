import subprocess
import os
from os import path
import sys
import DESops as d
from lts import StateMachine


this_file = path.dirname(path.abspath(__file__))


class Repair:
    def __init__(self, plant, property, alphabet, controllable, observable):
        self.plant = plant
        self.property = property
        self.controllable = controllable
        self.observable = observable
        self.alphabet = alphabet
    
    def synthesize(self):
        if not path.exists("tmp"):
            os.mkdir("tmp")
        plant = list(map(lambda x: self.to_fsm(x), self.plant))
        plant = plant[0] if len(plant) == 1 else d.composition.parallel(*plant)
        p = list(map(lambda x: self.to_fsm(x, extend_alphabet=True), self.property))
        p = p[0] if len(p) == 1 else d.composition.parallel(*p)

        L = d.supervisor.supremal_sublanguage(plant, p, prefix_closed=False, mode=d.supervisor.Mode.CONTROLLABLE_NORMAL)
        L = d.composition.observer(L)
        if len(L.vs) != 0:
            self.fsm2lts(L, "sup", self.observable)
        else:
            print("Cannot find a controller")
    
    def to_fsm(self, file, extend_alphabet=False):
        if file.endswith(".lts"):
            return self.lts2fsm(file, extend_alphabet)
        elif file.endswith(".fsm"):
            if extend_alphabet:
                m = StateMachine.from_fsm(file)
                m = m.extend_alphabet(self.alphabet)
                tmp_fsm = f"tmp/{path.basename(file)}.fsm"
                m.to_fsm(self.controllable, self.observable, tmp_fsm)
                return d.read_fsm(tmp_fsm)
            else:
                return d.read_fsm(file)
        elif file.endswith(".json"):
            return self.json2fsm(file, extend_alphabet)
        else:
            raise Exception("Unknown input file type")

    def lts2fsm(self, file, extend_alphabet=False):
        print("Convert", file, "to fsm model")
        name = path.basename(file)
        tmp_json = f"tmp/{name}.json"
        with open(tmp_json, "w") as f:
            subprocess.run([
                "java",
                "-cp",
                path.join(this_file, "../bin/robustness-calculator.jar"),
                "edu.cmu.isr.robust.ltsa.LTSAHelperKt",
                "convert",
                "--lts",
                file,
            ], stdout=f)
        return self.json2fsm(tmp_json, extend_alphabet)
    
    def json2fsm(self, file, extend_alphabet=False):
        m = StateMachine.from_json(file)
        if extend_alphabet:
            m = m.extend_alphabet(self.alphabet)
        tmp_fsm = f"tmp/{path.basename(file)}.fsm"
        m.to_fsm(self.controllable, self.observable, tmp_fsm)
        return d.read_fsm(tmp_fsm)

    def fsm2lts(self, fsm, name, alphabet):
        fsm_file = f"tmp/{name}.fsm"
        d.write_fsm(fsm_file, fsm)
        m = StateMachine.from_fsm(fsm_file, alphabet)
        json_file = f"tmp/{name}.json"
        m.to_json(json_file)
        lts = subprocess.check_output([
            "java",
            "-cp",
            path.join(this_file, "../bin/robustness-calculator.jar"),
            "edu.cmu.isr.robust.ltsa.LTSAHelperKt",
            "convert",
            "--json",
            json_file,
        ], text=True)
        print("=============================")
        print("Synthesized Controller:")
        print(lts)
    
    @staticmethod
    def abstract(file, abs_set):
        print("Abstract", file, "by", abs_set)
        name = path.basename(file)
        tmp_json = f"tmp/abs_{name}.json"
        with open(tmp_json, "w") as f:
            subprocess.run([
                "java",
                "-cp",
                path.join(this_file, "../bin/robustness-calculator.jar"),
                "edu.cmu.isr.robust.ltsa.LTSAHelperKt",
                "abstract",
                "-m",
                file,
                "-f",
                "json",
                *abs_set
            ], stdout=f)
        return tmp_json

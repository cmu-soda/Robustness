import subprocess
import os
from os import path
import sys
import DESops as d
from lts import StateMachine


this_file = path.dirname(path.abspath(__file__))


# Definition of priority
HIGH = 0
MEDIUM = 1
LOW = 3

class Repair:
    def __init__(self, sys, env_p, safety, desired, alphabet, controllable, observable):
        # the model files of the system, type: list(str)
        self.sys = sys
        # the model files of the deviated environment model, type: list(str)
        self.env_p = env_p
        # the model files of the safety property, type: list(str)
        self.safety = safety
        # a map from importance to model files of the desired behavior, type: Priority -> list(str)
        self.desired = desired
        # a list of events for \alpha M \cup \alpha E, type: list(str)
        self.alphabet = alphabet
        # a map from cost to list of controllable events, type: Priority -> list(str)
        self.controllable = controllable
        # a map from cost to list of observable events, type: Priority -> list(str)
        self.observable = observable
        # TODO:
        # assert controllable should be a subset of observable
        # assert False, "Controllable should be a subset of observable"
        # assert observable is a subset of alphabet
        # assert False, "Observable events should be a subset of the alphabet"
    
    def _synthesize(self, desired, controllable, observable):
        """
        Given a set of desired behavior, controllable events, and observable events,
        this function returns a controller by invoking DESops. None is returned when
        no such controller can be found.
        """
        if not path.exists("tmp"):
            os.mkdir("tmp")
        plant = list(map(lambda x: self.to_fsm(x, controllable, observable), self.sys + self.env_p))
        plant = plant[0] if len(plant) == 1 else d.composition.parallel(*plant)
        p = list(map(lambda x: self.to_fsm(x, controllable, observable, extend_alphabet=True), self.safety + desired))
        p = p[0] if len(p) == 1 else d.composition.parallel(*p)

        L = d.supervisor.supremal_sublanguage(plant, p, prefix_closed=False, mode=d.supervisor.Mode.CONTROLLABLE_NORMAL)
        L = d.composition.observer(L)
        if len(L.vs) != 0:
            # return self.fsm2lts(L, "sup", observable)
            return L, plant, p  # return the supervisor, the plant, and the property model
        else:
            return None
    
    def minimize_controller(self, plant, C, controllable, observable):
        """
        Given a plant, controller, controllable events, and observable events, remove unnecessary
        controllable/observable events to minimize its cost.
        """
        # Convert C to a StateMachine object
        # print(self.fsm2lts(C, "C", observable))
        d.write_fsm("tmp/C.fsm", C)
        C = StateMachine.from_fsm("tmp/C.fsm", observable)
        # Hide the unobservable events in the plant and convert it to StateMachine object
        plant = d.composition.observer(plant)
        d.write_fsm("tmp/G.fsm", plant)
        plant = StateMachine.from_fsm("tmp/G.fsm", observable)

        sup = self.construct_supervisor(plant, C, controllable, observable)
        all_states = sup.all_states()
        out_trans = sup.out_trans()

        can_uc = observable.copy()
        for s in all_states:
            for a in can_uc.copy():
                if sup.next_state(s, a) == None:
                    can_uc.remove(a)
        can_uo = can_uc.copy()
        for s in all_states:
            for a in can_uo.copy():
                if [s, sup.alphabet.index(a), s] not in out_trans[s]:
                    can_uo.remove(a)
        # Hide unobservable events in minimized controller
        min_controllable = set(controllable) - set(can_uc)
        min_observable = set(observable) - set(can_uo)
        sup.to_fsm(min_controllable, min_observable, "tmp/sup.fsm")
        sup = d.read_fsm("tmp/sup.fsm")
        sup = d.composition.observer(sup)
        
        print("Ec:", min_controllable)
        print("Eo:", min_observable)
        print(self.fsm2lts(sup, "sup", min_observable))
        return sup, min_controllable, min_observable

    def construct_supervisor(self, plant, C, controllable, observable):
        qc, qg = [0], [0]
        new_trans = C.transitions.copy()
        visited = set()
        while len(qc) > 0:
            sc, sg = qc.pop(0), qg.pop(0)
            if sc in visited:
                continue
            visited.add(sc)
            for a in observable:
                sc_p = C.next_state(sc, a) # assuming C and plant are deterministic
                sg_p = plant.next_state(sg, a)
                if sc_p != None:
                    qc.append(sc_p)
                    qg.append(sg_p)
                elif a not in controllable: # uncontrollable event
                    new_trans.append([sc, C.alphabet.index(a), sc])
                elif sg_p == None: # controllable but not defined in G
                    new_trans.append([sc, C.alphabet.index(a), sc])
        return StateMachine("sup", new_trans, C.alphabet, C.accept)
    
    def synthesize(self, n):
        """
        Given maximum number of solutions n, return a list of n solutions.
        """
        # TODO:
        # initialization (computing weights)
        for _ in range(n):
            pass
            # desired = self.nextBestDesiredBeh(desired)
            # C, plant, _ = self._synthesize(desired, max_controllable, max_observable)
            # sup, controllable, observable = self.minimize_controller(plant, C, max_controllable, max_observable)
            # utility = self.compute_utility(desired, controllable, observable)
            # result = {
            #     "M_prime": self.compose_M_prime(sup),
            #     "controllable": controllable,
            #     "observable": observable,
            #     "utility": utility
            # }
            # yield result
    
    def nextBestDesiredBeh(self, desired):
        """
        Given the current desired behavior that are used to compute a controller, returns the
        next best set of desired behavior that minimizes the lost in utility.
        """
    
    def compute_utility(self, desired, controllable, observable):
        """
        Given the desired behavior that are satisfied, the controllable events needed, and
        the observable events needed, return the utility value.
        """
    
    def compose_M_prime(self, sup):
        """
        Given a controller, compose it with the original system M to get the new design M'
        """
    
    # def tmp_file_suffix(self, controllable, observable):
    #     s = set(controllable).union(set(observable))
    #     return f"{hash(tuple(s)):X}"

    def to_fsm(self, file, controllable, observable, extend_alphabet=False):
        if file.endswith(".lts"):
            return self.lts2fsm(file, controllable, observable, extend_alphabet)
        elif file.endswith(".fsm"):
            if extend_alphabet:
                m = StateMachine.from_fsm(file)
                m = m.extend_alphabet(self.alphabet)
                tmp_fsm = f"tmp/{path.basename(file)}.fsm"
                m.to_fsm(controllable, observable, tmp_fsm)
                return d.read_fsm(tmp_fsm)
            else:
                return d.read_fsm(file)
        elif file.endswith(".json"):
            return self.json2fsm(file, controllable, observable, extend_alphabet)
        else:
            raise Exception("Unknown input file type")

    def lts2fsm(self, file, controllable, observable, extend_alphabet=False):
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
        return self.json2fsm(tmp_json, controllable, observable, extend_alphabet)
    
    def json2fsm(self, file, controllable, observable, extend_alphabet=False):
        m = StateMachine.from_json(file)
        if extend_alphabet:
            m = m.extend_alphabet(self.alphabet)
        tmp_fsm = f"tmp/{path.basename(file)}.fsm"
        m.to_fsm(controllable, observable, tmp_fsm)
        return d.read_fsm(tmp_fsm)

    def fsm2lts(self, fsm, name, alphabet):
        fsm_file = f"tmp/{name}.fsm"
        d.write_fsm(fsm_file, fsm)
        m = StateMachine.from_fsm(fsm_file, alphabet)
        json_file = f"tmp/{name}.json"
        m.to_json(json_file)
        return self.json2lts(json_file)
    
    def json2lts(self, file):
        lts = subprocess.check_output([
            "java",
            "-cp",
            path.join(this_file, "../bin/robustness-calculator.jar"),
            "edu.cmu.isr.robust.ltsa.LTSAHelperKt",
            "convert",
            "--json",
            file,
        ], text=True)
        return lts
    
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

import subprocess
import os
from os import path
from random import random
import shutil
import DESops as d
from lts import StateMachine


this_file = path.dirname(path.abspath(__file__))


# Definition of priority
PRIORITY0 = 0
PRIORITY1 = 1
PRIORITY2 = 2
PRIORITY3 = 3

class Repair:
    def __init__(self, sys, env_p, safety, preferred, alphabet, controllable, observable):
        # the model files of the system, type: list(str)
        self.sys = list(map(lambda x: self.fsp2lts(x), sys))
        # the model files of the deviated environment model, type: list(str)
        self.env_p = list(map(lambda x: self.fsp2lts(x), env_p))
        # the model files of the safety property, type: list(str)
        self.safety = list(map(lambda x: self.fsp2lts(x), safety))
        # a map from importance to model files of the preferred behavior, type: Priority -> list(str)
        self.preferred = preferred # list(map(lambda x: self.fsp2lts(x), preferred))
        # a list of events for \alpha M \cup \alpha E, type: list(str)
        self.alphabet = alphabet
        # a map from cost to list of controllable events, type: Priority -> list(str)
        self.controllable = controllable
        # a map from cost to list of observable events, type: Priority -> list(str)
        self.observable = observable

        self.check_preferred_cache = {}


        # TODO:
        # assert controllable should be a subset of observable
        # assert False, "Controllable should be a subset of observable"
        # assert observable is a subset of alphabet
        # assert False, "Observable events should be a subset of the alphabet"

        if path.exists("tmp"):
            shutil.rmtree("tmp")
        os.mkdir("tmp")
    
    def _synthesize(self, controllable, observable):
        """
        Given a set of preferred behavior, controllable events, and observable events,
        this function returns a controller by invoking DESops. None is returned when
        no such controller can be found.
        """
        plant = list(map(lambda x: self.lts2fsm(x, controllable, observable), self.sys + self.env_p))
        plant = plant[0] if len(plant) == 1 else d.composition.parallel(*plant)
        p = list(map(lambda x: self.lts2fsm(x, controllable, observable, extend_alphabet=True), self.safety))
        p = p[0] if len(p) == 1 else d.composition.parallel(*p)

        L = d.supervisor.supremal_sublanguage(plant, p, prefix_closed=True, mode=d.supervisor.Mode.CONTROLLABLE_NORMAL)
        L = d.composition.observer(L)
        if len(L.vs) != 0:
            return L, plant, p  # return the supervisor, the plant, and the property model
        else:
            return None
    
    def remove_unnecessary(self, plant, sup_plant, controllable, observable):
        """
        Given a plant, controller, controllable events, and observable events, remove unnecessary
        controllable/observable events to minimize its cost.
        """
        # Convert Sp/G to a StateMachine object
        sup_plant = self.fsm2lts(sup_plant, observable)
        # Hide the unobservable events in the plant and convert it to StateMachine object
        plant = d.composition.observer(plant)
        plant = self.fsm2lts(plant, observable)

        sup = self.construct_supervisor(plant, sup_plant, controllable, observable)
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
        min_controllable = set(controllable) - set(can_uc)
        min_observable = set(observable) - set(can_uo)

        # FIXME: Remove all the self-loops for uncontrollable events
        # sup.transitions = list(filter(lambda x: x[0] != x[2] or sup.alphabet[x[1]] in min_controllable, sup.transitions))
        # Hide unobservable events
        sup = self.lts2fsm(sup, min_controllable, min_observable, name="sup")
        sup = d.composition.observer(sup)
        
        print("Ec:", min_controllable)
        print("Eo:", min_observable)
        # print(self.fsm2fsp(sup, min_observable, name="min_sup"))
        return sup, min_controllable, min_observable

    def construct_supervisor(self, plant, sup_plant, controllable, observable):
        qc, qg = [0], [0]
        new_trans = sup_plant.transitions.copy()
        visited = set()
        while len(qc) > 0:
            sc, sg = qc.pop(0), qg.pop(0)
            if sc in visited:
                continue
            visited.add(sc)
            for a in observable:
                sc_p = sup_plant.next_state(sc, a) # assuming Sp/G and plant are deterministic
                sg_p = plant.next_state(sg, a)
                if sc_p != None:
                    qc.append(sc_p)
                    qg.append(sg_p)
                elif a not in controllable: # uncontrollable event, make admissible
                    new_trans.append([sc, sup_plant.alphabet.index(a), sc])
                elif sg_p == None: # controllable but not defined in G, make redundant
                    new_trans.append([sc, sup_plant.alphabet.index(a), sc])
        return StateMachine("sup", new_trans, sup_plant.alphabet, sup_plant.accept)
    
    def compute_weights(self):
        """
        Given the priority ranking that the user provides, compute the positive utilities for preferred behavior
        and the negative cost for making certain events controllable and/or observable. Return dictionary with this information
        and also returns list of weights for future reference. (DONE)
        """

        #maintain dictionary for preferred, controllable, and observable
        preferred_dict = self.preferred
        controllable_dict = self.controllable
        observable_dict = self.observable
        alphabet = self.alphabet
        preferred = []
        for key in self.preferred:
            for beh in self.preferred[key]:
                preferred.append(beh)


        #initialize weight dictionary
        weight_dict = {} 
        for i in alphabet:
            weight_dict[i] = ["c", "o"]
        for i in preferred:
            weight_dict[i] = "d"

        #insert behaviors that have no cost into dictionary of weights
        for i in controllable_dict[PRIORITY0]:
            weight_dict[i][0] = 0
        for i in observable_dict[PRIORITY0]:
            weight_dict[i][1] = 0
        
        #intialize list of pairs with polar opposite costs
        category_list = []
        category_list.append([preferred_dict[PRIORITY1], controllable_dict[PRIORITY1], observable_dict[PRIORITY1]]) #first category
        category_list.append([preferred_dict[PRIORITY2], controllable_dict[PRIORITY2], observable_dict[PRIORITY2]]) #second category
        category_list.append([preferred_dict[PRIORITY3], controllable_dict[PRIORITY3], observable_dict[PRIORITY3]]) #third category


        #initialize weights for tiers and list of weight absolute values that are assigned
        total_weight = 0
        weight_list = []

        #assign weights and grow the weight list
        #give poistive weights to first list in category and negative weights to second list in category
        #compute new weight in order to maintain hierarchy by storing absolute value sum of previous weights
        for i in category_list:
            curr_weight = total_weight + 1
            for j in range(len(i)):
                if j == 0:
                    for k in i[j]:
                        weight_dict[k] = curr_weight
                        total_weight += curr_weight
                elif j == 1:
                    for k in i[j]:
                        weight_dict[k][0] = -1*curr_weight
                        total_weight += curr_weight
                else:
                    for k in i[j]:
                        weight_dict[k][1] = -1*curr_weight
                        total_weight += curr_weight
            weight_list.append(curr_weight)
    
        #return weight_dict, weight_list
        return weight_dict, weight_list
    
    def compute_cost(self, preferred_behavior, min_controllable, min_observable, weight_dict):
        """
        Given preferred behaviors, minimum list of observable behavior, minimum list of controllable behavior, 
        and the weight dictionary, compute the total utility of a program. 
        """
        utility = 0
        for i in preferred_behavior:
            utility += weight_dict[i]
        
        for i in min_controllable:
            utility += weight_dict[i][0]
        
        for i in min_observable:
            utility += weight_dict[i][1]
        
        return utility 
    
    def check_preferred(self, minS, controllable, observable, preferred):
        """
        Given some minimum supervisor, a set of controllable events, a set of observable events, 
        and a set of preferred behavior, checks how much preferred behavior is satisfied
        """
        fulfilled_preferred = []

        M_prime = self.compose_M_prime(minS, controllable, observable)
        for p in preferred:
            key = (tuple(controllable), tuple(observable), p)
            if key in self.check_preferred_cache:
                if self.check_preferred_cache[key]:
                    fulfilled_preferred.append(p)
                    print("Cache hit for:", key)
                continue
            self.check_preferred_cache[key] = False

            p_fsm = self.fsp2fsm(p, controllable, observable)
            M_prime.Euo = p_fsm.Euo.union(M_prime.events - p_fsm.events)
            M_prime.Euc = p_fsm.Euc.union(M_prime.events - p_fsm.events)
            M_prime_observed = d.composition.observer(M_prime) # seems to have performance issue :C
            if d.compare_language(d.composition.parallel(M_prime_observed, p_fsm), p_fsm):
                fulfilled_preferred.append(p)
                self.check_preferred_cache[key] = True

        return fulfilled_preferred
    
    def synthesize(self, n):
        """
        Given maximum number of solutions n, return a list of up to k solutions, prioritizng fulfillment of preferred behavior.
        """
        preferred = []
        for key in self.preferred:
            for beh in self.preferred[key]:
                preferred.append(beh)


        #alphabet = self.alphabet
        #C, plant, _ = self._synthesize(alphabet, alphabet)
        #S = self.construct_supervisor(plant, C, alphabet, alphabet)
        #minS = self.remove_unnecessary(plant, S, alphabet, alphabet)
        #DF = self.check_preferred(minS, alphabet, alphabet, preferred)

        return DF





        # TODO:
        # initialization (computing weights)
        for _ in range(n):
            pass
            # preferred = self.nextBestpreferredBeh(preferred)
            # TODO: Is Sup || G already the M' that minimize the difference?
            # C, plant, _ = self._synthesize(preferred, max_controllable, max_observable)
            # sup, controllable, observable = self.remove_unnecessary(plant, C, max_controllable, max_observable)
            # utility = self.compute_utility(preferred, controllable, observable)
            # result = {
            #     "M_prime": self.compose_M_prime(sup),
            #     "controllable": controllable,
            #     "observable": observable,
            #     "utility": utility
            # }
            # yield result
    


    
    def compose_M_prime(self, sup, controllable, observable):
        """
        Given a controller, compose it with the original system M to get the new design M'
        """
        M = list(map(lambda x: self.lts2fsm(x, controllable, observable), self.sys))
        M = M[0] if len(M) == 1 else d.composition.parallel(*M)
        M_prime = d.composition.parallel(M, sup)
        return M_prime

    def file2fsm(self, file, controllable, observable, extend_alphabet=False):
        name = path.basename(file)
        if file.endswith(".lts"):
            return self.fsp2fsm(file, controllable, observable, extend_alphabet)
        elif file.endswith(".fsm"):
            if extend_alphabet:
                m = StateMachine.from_fsm(file)
                return self.lts2fsm(m, controllable, observable, extend_alphabet=extend_alphabet, name=name)
            else:
                return d.read_fsm(file)
        elif file.endswith(".json"):
            m = StateMachine.from_json(file)
            return self.lts2fsm(m, controllable, observable, extend_alphabet=extend_alphabet, name=name)
        else:
            raise Exception("Unknown input file type")
    
    def lts2fsm(self, m, controllable, observable, name=None, extend_alphabet=False):
        if extend_alphabet:
            m = m.extend_alphabet(self.alphabet)
        tmp = f"tmp/{name}.fsm" if name != None else f"tmp/tmp.{random() * 1000_000}.fsm"
        m.to_fsm(controllable, observable, tmp)
        return d.read_fsm(tmp)

    def fsp2fsm(self, file, controllable, observable, extend_alphabet=False):
        name = path.basename(file)
        m = self.fsp2lts(file)
        return self.lts2fsm(m, controllable, observable, extend_alphabet=extend_alphabet, name=name)
    
    def fsm2lts(self, obj, alphabet=None, name=None, extend_alphabet=False):
        tmp = f"tmp/{name}.fsm" if name != None else f"tmp/tmp.{random() * 1000_000}.fsm"
        d.write_fsm(tmp, obj)
        m = StateMachine.from_fsm(tmp, alphabet)
        if extend_alphabet:
            m = m.extend_alphabet(self.alphabet)
        return m
    
    def fsm2fsp(self, obj, alphabet=None, name=None):
        m = self.fsm2lts(obj, alphabet, name)
        tmp = f"tmp/{name}.json" if name != None else f"tmp/tmp.{random() * 1000_000}.json"
        m.to_json(tmp)
        lts = subprocess.check_output([
            "java",
            "-jar",
            path.join(this_file, "./bin/ltsa-helper.jar"),
            "convert",
            "--json",
            tmp,
        ], text=True)
        return lts
    
    def fsp2lts(self, file):
        print(f"Read {file}...")
        name = path.basename(file)
        tmp_json = f"tmp/{name}.json"
        with open(tmp_json, "w") as f:
            subprocess.run([
                "java",
                "-jar",
                path.join(this_file, "./bin/ltsa-helper.jar"),
                "convert",
                "--lts",
                file,
            ], stdout=f)
        return StateMachine.from_json(tmp_json)
    
    @staticmethod
    def abstract(file, abs_set):
        print("Abstract", file, "by", abs_set)
        name = path.basename(file)
        tmp_json = f"tmp/abs_{name}.json"
        with open(tmp_json, "w") as f:
            subprocess.run([
                "java",
                "-jar",
                path.join(this_file, "./bin/ltsa-helper.jar"),
                "abstract",
                "-m",
                file,
                "-f",
                "json",
                *abs_set
            ], stdout=f)
        return tmp_json

import subprocess
import os
from os import path
from random import random
import shutil
import DESops as d
from lts import StateMachine
import itertools

this_file = path.dirname(path.abspath(__file__))


# Definition of priority
PRIORITY0 = 0
PRIORITY1 = 1
PRIORITY2 = 2
PRIORITY3 = 3

class Repair:
    def __init__(self, sys, env_p, safety, preferred, alphabet, controllable, observable):

        if path.exists("tmp"):
            shutil.rmtree("tmp")
        os.mkdir("tmp")

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
        self.synthesize_cache = {}


        # TODO:
        # assert controllable should be a subset of observable
        # assert False, "Controllable should be a subset of observable"
        # assert observable is a subset of alphabet
        # assert False, "Observable events should be a subset of the alphabet
    
    def _synthesize(self, controllable, observable):
        """
        Given a set of preferred behavior, controllable events, and observable events,
        this function returns a controller by invoking DESops. None is returned when
        no such controller can be found.
        """
        key = (tuple(controllable), tuple(observable))
        if key in self.synthesize_cache:
            print("Synthesize cache hit: ", key)
            return self.synthesize_cache[key]

        plant = list(map(lambda x: self.lts2fsm(x, controllable, observable), self.sys + self.env_p))
        plant = plant[0] if len(plant) == 1 else d.composition.parallel(*plant)
        p = list(map(lambda x: self.lts2fsm(x, controllable, observable, extend_alphabet=True), self.safety))
        p = p[0] if len(p) == 1 else d.composition.parallel(*p)

        L = d.supervisor.supremal_sublanguage(plant, p, prefix_closed=True, mode=d.supervisor.Mode.CONTROLLABLE_NORMAL)
        L = d.composition.observer(L)

        # return the supervisor, the plant, and the property model
        self.synthesize_cache[key] = (L, plant, p) if len(L.vs) != 0 else None
        return self.synthesize_cache[key]
    
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
    
    def minimize(self, minS, controllable, observable, preferred):
        """
        Given some set of controllable and observable events and supervisor,
        minimize the supervisor and returns the smallest set of controllable and observable
        events needed for some level of preferred behavior
        """

        
        #parition all events that cane be made controllable and/or observable into lists by priority
        high_priority_controllable = [c for c in controllable if c in self.controllable[PRIORITY3]] #High Priority controllable events
        medium_priority_controllable = [c for c in controllable if c in self.controllable[PRIORITY2]] #Medium Priority controllable events
        low_priority_controllable = [c for c in controllable if c in self.controllable[PRIORITY1]] #Low Priority controllable events
        high_priority_observable = [o for o in observable if o in self.observable[PRIORITY3]] #High Priority Observable Events
        medium_priority_observable = [o for o in observable if o in self.observable[PRIORITY2]] #Medium Priority Observable Events
        low_priority_observable = [o for o in observable if o in self.observable[PRIORITY1]] #Low Priority Observable Events

        #Dictionary where events are separated according to priority
        #Key is 0, 1, or 2 where 0 is high priortiy, 1 is medium priority, and 2 is low priority
        #Value is a list where first list is controllable events andthe second list is observable events 
        actions_dict = {0: [high_priority_controllable, high_priority_observable], 1 : [medium_priority_controllable, medium_priority_observable], 
                        2: [low_priority_controllable, low_priority_observable]}
        
      
        #initialize save
        #save is a placeholder which saves the last set of possible minimizations that passed if at some interation there are no minimizations that pass
        save = []
        gp_list = [{"c": set(controllable), "o": set(observable), "minS": minS}] #gp_list is the est of good possibilities, initialize with nothing removed as a good possibility

        #By iterating i in range(3) we examine first the high priority events, then medium priority events and then low priority events
        for i in range(3):
            j = 0 #initialize the number of iterations when dealing with a priority group
            while (len(gp_list) != 0 and j < (len(actions_dict[i][0]) + len(actions_dict[i][1]))): #continue adding events of a priority until it provides to be futile or it isimpossible to add more
                    save = gp_list #save the gp_list, the list of good possibilities from the last iteration
                    p_list = [] #start the new list of possibilities
                    for event_dict in save: #iterate through good possibilities from last iteration
                        for c in actions_dict[i][0]: #look at all controllable actions in the priority and remove when possible to create new minimization
                            if c in event_dict["c"]:
                                controllable_set = event_dict["c"].copy()
                                controllable_set.remove(c)
                                new_event_dict = {"c": controllable_set, "o": event_dict["o"]}
                                p_list.append(new_event_dict)
                        for o in actions_dict[i][1]: #look at all observable actions in the priority and remove when possible to create minimization
                            if (o in event_dict["o"]) and (o not in event_dict["c"]):
                                observable_set = event_dict["o"].copy()
                                observable_set.remove(o)
                                new_event_dict = {"c": event_dict["c"], "o": observable_set}
                                p_list.append(new_event_dict)
                

                    #remove duplicates
                    cp_list = []
                    for event_dict in p_list:
                        if event_dict not in cp_list:
                            cp_list.append(event_dict)
                
                    
                   
                    gp_list = [] #initialize gp_list

                    #keep only the minimizations which work
                    for event_dict in cp_list:
                        C, plant, _ = self._synthesize(event_dict["c"], event_dict["o"]) #synthesize with the appropriate controllable/observable events
                        minS = self.construct_supervisor(plant, C, event_dict["c"], event_dict["o"]) #get just the supervisor
                        if self.check_preferred(minS, event_dict["c"],event_dict["o"], preferred) == preferred: #add minimization if preferred behavior maintained
                            event_dict["minS"] = minS #update the minS
                            gp_list.append(event_dict) 

                    j = j + 1 #iterate the counter for how many events of the priority have been considered

            #if while loop was broken because no permissible minimization happened, then use the saved minimizations as the best previous
            if len(gp_list) == 0:
                gp_list = save 

        best_minimization = gp_list.pop() #take one randomly once out of while loop

        return best_minimization["minS"], list(best_minimization["c"]), list(best_minimization["o"]) #return the appropriate information
    


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
    
        #return weight_dict, weight_list
        return weight_dict
    
    def compute_util_cost(self, preferred_behavior, min_controllable, min_observable, weight_dict):
        """
        Given preferred behaviors, minimum list of observable behavior, minimum list of controllable behavior, 
        and the weight dictionary, compute the total utility of a program and return the cost separately. 
        """

        #initialize cost and utility
        utility = 0
        cost = 0

        #do computations for preferred behavior, controllable, and observable
        for i in preferred_behavior:
            utility += weight_dict[i]
        
        for i in min_controllable:
            utility += weight_dict[i][0]
            cost += weight_dict[i][0]
        
        for i in min_observable:
            utility += weight_dict[i][1]
            cost =+ weight_dict[i][1]
        
        return utility, cost 
    
    
    
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
                    print("Check preferred cache hit:", key)
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

        #collect all preferred behavior
        preferred = []
        for key in self.preferred:
            for beh in self.preferred[key]:
                preferred.append(beh)

        
        #find dictionary of weights by actions, preferred behavior and weights assigned by tiers
        weight_dict =  self.compute_weights()


        #first synthesize with all aphabet, then find supervisor, then remove unecessary actions, and check which preferred behavior are satisfied
        alphabet = self.alphabet
        C, plant, _ = self._synthesize(alphabet, alphabet)
        minS, controllable, observable = self.remove_unnecessary(plant, C, alphabet, alphabet)
        DF = self.check_preferred(minS, controllable, observable, preferred)


        #trim out the preferred behaviors that could never be satisfied
        l1 = [action for action in self.preferred[PRIORITY1] if action in DF]
        l2 = [action for action in self.preferred[PRIORITY2] if action in  DF]
        l3 = [action for action in self.preferred[PRIORITY3] if action in DF]

        #initialize list of controllers
        controllers = []

        #intialize number of controllers considered
        t = 0

        #initialize the least cost experienced overall as a very negative cost, this stands for -infinity
        min_cost = -444444444444


        #increase behavior that won't be satisfied in the lexicographic order
        while t < n:
            for i in range(len(l3) + 1):
                for j in range(len(l2) + 1):
                    for k in range(len(l1) + 1):
                        possible_controllers = []

                        #for this partition of lost behavior, find all possible subsets from each category
                        beh_1_subsets = list(itertools.combinations(l1, k))
                        beh_2_subsets = list(itertools.combinations(l2, j))
                        beh_3_subsets = list(itertools.combinations(l3, i))

                        #initialize the best cost for this amount of preferred behaviornot satisfied to be a very negative cost, this stands for -infinity
                        best_cost = -44444444444444
                        #find all combos of preferred behaviors that are going to be removed with this cost
                        for l in beh_1_subsets:
                            for m in beh_2_subsets:
                                for o in beh_3_subsets:
                                    #remove behavior that we don't care about anymore
                                    remove_behavior = l + m + o
                                    DF_subset = DF
                                    for p in remove_behavior:
                                        DF_subset.remove(p)
                                
                                    #find result that is found by minimizing
                                    sup, min_controllable, min_observable = self.minimize(minS, controllable, observable, DF_subset)
                                    #compute the total utility and the cost of such a minimization
                                    total_utility, cost = self.compute_util_cost(DF_subset, min_controllable, min_observable, weight_dict)
                                    t += 1
                            

                                    #check how cost of of this set of removed preferred behavior compares with the best cost thus far
                                    if cost < best_cost: #if cost is worse than best, then ignore it
                                        continue
                                    else:
                                        result = {
                                        "M_prime": sup,
                                        "controllable": min_controllable,
                                        "observable": min_observable, 
                                        "utility": total_utility
                                        }
                                        if cost > best_cost: #if cost is better, then clear out all others, update best cost and then start a new list
                                            best_cost =cost
                                            possible_controllers = [result]
                                        else: #if cost is sam as best, then add to list
                                            possible_controllers.append(result)
                    
                             
                        #if the best cost among these subsets exceeds prior best cost add to controllers 
                        if best_cost > min_cost:
                            min_cost = best_cost
                            controllers.extend(possible_controllers)

        #composes Mprimefor all once we know that these are what we want and updates
        for controller in controllers:
            controller["M_prime"] = self.compose_M_prime(controller["M_prime"], controller["controllable"], controller["observable"])

        #returns all controllers
        return controllers

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





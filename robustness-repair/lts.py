import json
from os import path
import DESops as d

class StateMachine:
    def __init__(self, name, transitions, alphabet, accept=None):
        self.name = name
        self.transitions = transitions
        self.alphabet = alphabet
        self.accept = set() if accept == None else accept
        self._out_trans = None
        self._all_states = None
    
    def extend_alphabet(self, alphabet):
        new_trans = self.transitions.copy()
        new_alphabet = self.alphabet.copy()
        for a in set(alphabet) - set(self.alphabet):
            new_alphabet.append(a)
        for s in self.all_states():
            for a in range(len(self.alphabet), len(new_alphabet)):
                new_trans.append([s, a, s])
        return StateMachine(self.name, new_trans, new_alphabet, self.accept)
    
    def next_state(self, s, a):
        out_trans = self.out_trans()
        i = self.alphabet.index(a)
        if s in out_trans:
            for t in out_trans[s]:
                if t[1] == i:
                    return t[2]
        return None
    
    @staticmethod
    def from_json(file):
        with open(file) as f:
            obj = json.load(f)
            m = StateMachine(obj["process"], obj["transitions"], obj["alphabet"])
            m.accept = m.all_states()
            return m

    @staticmethod
    def from_fsm(fsm, alphabet=None, name=None):
        if alphabet == None:
            alphabet = ["_tau_"]
        else:
            assert "_tau_" not in alphabet, "Tau should not be in the alphabet of FSM"
            alphabet = ["_tau_"] + list(alphabet)
        
        def alphabet_idx(t):
            if t in alphabet:
                return alphabet.index(t)
            else:
                # assert False, "ERROR! All alphabet should be included already."
                alphabet.append(t)
                return len(alphabet) - 1
        
        transitions = []
        for edge in fsm.es:
            transitions.append([edge.source, alphabet_idx(edge['label'].label), edge.target])
        accept = set(filter(lambda x: fsm.vs["marked"][x], range(fsm.vcount())))
        return StateMachine(name, transitions, alphabet, accept)

    def out_trans(self):
        if self._out_trans == None:
            out = {}
            for t in self.transitions:
                if t[0] not in out:
                    out[t[0]] = []
                out[t[0]].append(t)
            self._out_trans = out
        return self._out_trans
    
    def all_states(self):
        if self._all_states == None:
            states = set([0])
            for t in self.transitions:
                states.add(t[0])
                states.add(t[2])
            self._all_states = states
        return self._all_states

    def to_fsm(self, controllable, observable):
        fsm = d.DFA()
        fsm.add_vertices(max(self.all_states()) + 1)
        fsm.add_edges(
            [(t[0], t[2]) for t in self.transitions],
            [d.Event(self.alphabet[t[1]]) for t in self.transitions]
        )
        # for t in self.transitions:
        #     fsm.add_edge(t[0], t[2], d.Event(self.alphabet[t[1]]))
        fsm.vs["marked"] = [1 if i in self.accept else 0 for i in range(fsm.vcount())]
        fsm.Euc = set(filter(lambda e: e.label not in controllable, fsm.events))
        fsm.Euo = set(filter(lambda e: e.label not in observable, fsm.events))
        return fsm
    
    def to_json(self, file=None):
        obj = {
            "filename": file if file != None else "unknown",
            "process": self.name.upper(),
            "alphabet": self.alphabet,
            "transitions": self.transitions
        }
        if file != None:
            with open(file, "w") as f:
                json.dump(obj, f)
        return json.dumps(obj)

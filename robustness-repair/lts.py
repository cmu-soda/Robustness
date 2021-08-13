import json
from os import path

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
    def from_fsm(file, alphabet=None):
        if alphabet == None:
            alphabet = ["_tau_"]
        else:
            assert "_tau_" not in alphabet, "Tau should not be in the alphabet of FSM"
            alphabet = ["_tau_"] + list(alphabet)
        with open(file) as f:
            states = []
            transitions = []
            accept = set()

            def state_idx(s):
                if s in states:
                    return states.index(s)
                else:
                    states.append(s)
                    return len(states) - 1
            
            def alphabet_idx(t):
                if t in alphabet:
                    return alphabet.index(t)
                else:
                    # assert False, "ERROR! All alphabet should be included already."
                    alphabet.append(t)
                    return len(alphabet) - 1

            lines = list(filter(lambda x: x != "\n", f.readlines()))
            i = 1
            while i < len(lines):
                line_state = lines[i].strip().split("\t")
                s = state_idx(line_state[0])
                if line_state[1] == "1": # marked
                    accept.add(s)
                for _ in range(int(line_state[2])):
                    i += 1
                    line_t = lines[i].strip().split("\t")
                    t = alphabet_idx(line_t[0])
                    ss = state_idx(line_t[1])
                    transitions.append([s, t, ss])
                i += 1

            name = path.basename(file)
            name = name[:name.index(".")]
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

    def to_fsm(self, controllable, observable, file=None):
        c = ["c" if a in controllable else "uc" for a in self.alphabet]
        o = ["o" if a in observable else "uo" for a in self.alphabet]
        all_states = self.all_states()
        fsm = f"{len(all_states)}\n"

        out_trans = self.out_trans()
        for s in out_trans:
            fsm += "\n"
            trans = out_trans[s]
            fsm += f"State{s}\t{1 if s in self.accept else 0}\t{len(trans)}\n"
            for t in trans:
                fsm += f"{self.alphabet[t[1]]}\tState{t[2]}\t{c[t[1]]}\t{o[t[1]]}\n"
        
        for s in all_states - out_trans.keys():
            fsm += f"\nState{s}\t{1 if s in self.accept else 0}\t0\n"
        
        if file != None:
            with open(file, "w") as f:
                f.write(fsm)
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

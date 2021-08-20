import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import *

alphabet = [
    "alarm_rings",
    "alarm_silence",
    "battery_charge",
    "battery_spent",
    "enable_alarm",

    "line.1.change_settings",
    "line.1.clear_rate",
    "line.1.confirm_settings",
    "line.1.start_dispense",
    "line.1.dispense_main_med_flow",
    "line.1.erase_and_unlock_line",
    "line.1.flow_complete",
    "line.1.lock_line",
    "line.1.lock_unit",
    "line.1.set_rate",
    "line.1.unlock_unit",

    "plug_in",
    "power_failure",
    "turn_off",
    "turn_on",
    "unplug"
]

r = Repair(
    alg="fast",
    sys= ["power.lts", "lines.lts", "alarm.lts"],
    env_p = ["deviation.lts"],
    safety =["p.lts"],
    preferred={   # rank the preferred behavior by importance
        PRIORITY3: ["ideal.lts", "recover.lts"],
        PRIORITY2: [],
        PRIORITY1: [],  
        PRIORITY0: []
    },
    progress=["line.1.flow_complete"],
    alphabet=alphabet,
    controllable={  # rank the controllable events by cost
        PRIORITY3: [
            # Events of the line that are related to the physical world have high cost to control
            "line.1.erase_and_unlock_line",
            "line.1.lock_line",
            "line.1.lock_unit",
            "line.1.unlock_unit",
            # The line module has no control over other modules
        ],
        PRIORITY2: [],
        PRIORITY1: [
            # System events (events that need the human) of the line module have low cost to control
            "line.1.change_settings",
            "line.1.clear_rate",
            "line.1.confirm_settings",
            "line.1.set_rate",
        ],
        PRIORITY0: [
            # System events (events that do not need the human) of the line module are free to control
            "line.1.start_dispense",
            "line.1.dispense_main_med_flow",
            "line.1.flow_complete"
        ]
    },
    observable={    # rank observable events by cost
        PRIORITY3: [
            # Has high cost to observe some events of other modules related to the physical world.
            "battery_charge",
            "battery_spent",
            "plug_in",
            "unplug",
            "turn_off",
            "turn_on",
        ],
        PRIORITY2: [
            # Events of the line that are related to the physical world have moderate cost to observe
            "line.1.erase_and_unlock_line",
            "line.1.lock_line",
            "line.1.lock_unit",
            "line.1.unlock_unit",
            # Has moderate cost to observe some system events in other modules
            "alarm_silence",
            "enable_alarm",
        ],
        PRIORITY1: [],
        PRIORITY0: [
            # System events of the line module are free to observe
            "line.1.start_dispense",
            "line.1.dispense_main_med_flow",
            "line.1.flow_complete",
            "line.1.change_settings",
            "line.1.clear_rate",
            "line.1.confirm_settings",
            "line.1.set_rate",
        ]
    }
)

result = r.synthesize(3)
# print("Printing M' for each pareto-optimal...")
# for i, c in enumerate(result):
#     print("Solution", i)
#     print(r.fsm2fsp(c["M_prime"], c["observable"], name="M"))

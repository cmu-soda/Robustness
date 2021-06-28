import sys
from os import path

sys.path.append(path.dirname(path.dirname(path.dirname(path.abspath(__file__)))))

from repair import Repair


alphabet = set([
    "accept_server", "adversary_attacks", "check_info", "encr_device_to_platform", "encr_entity_to_platform",
    "encr_msg_entity_to_platform", "encr_msg_platform_to_device", "encr_platform_to_device", "encr_platform_to_server",
    "enter_id_password", "exit", "get_server", "gibberish", "hoax", "msg_device_to_platform", "msg_platform_to_server",
    "password", "receive_message", "reject", "reject_message", "reject_server", "select_server", "send_message",
    "server", "server_platform_connect", "success", "verify_message"
])

r = Repair(
    plant=["sys.lts", "env.lts"],
    property=["p.lts"], 
    alphabet=alphabet,
    controllable=set([
        "accept_server", "check_info", "encr_device_to_platform", "encr_entity_to_platform",
        "encr_msg_entity_to_platform", "encr_msg_platform_to_device", "encr_platform_to_device",
        "encr_platform_to_server", "enter_id_password", "exit", "get_server", "gibberish",
        "msg_device_to_platform", "msg_platform_to_server", "receive_message", "reject", "reject_server",
        "select_server", "send_message", "server", "server_platform_connect", "success"
    ]),
    observable=alphabet
)
r.synthesize()

# helpers.py

def to_short(value):
    value &= 0xFFFF
    if value & 0x8000:
        return value - 0x10000
    return value

REG_NAMES = {
    0: "PC", 1: "AC", 2: "SP", 3: "IR", 4: "TIR", 5: "0",
    6: "+1", 7: "-1", 8: "AMASK", 9: "SMASK", 10: "A", 11: "B",
    12: "C", 13: "D", 14: "E", 15: "F"
}

ALU_OPS = {0: "+", 1: "&", 2: "passA", 3: "invA"}
SH_OPS = {0: "", 1: ">>1", 2: "<<1"}

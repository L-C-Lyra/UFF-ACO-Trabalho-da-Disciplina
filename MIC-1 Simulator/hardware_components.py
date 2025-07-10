# hardware_components.py

from helpers import to_short

class Register:
    def __init__(self, name, value=0):
        self.name = name
        self.value = to_short(value)

    def set_value(self, value):
        self.value = to_short(value)

    def get_value(self):
        return self.value

class ALU:
    def __init__(self):
        self.output = 0
        self.n_bit = False
        self.z_bit = False

    def calculate(self, control, input_a, input_b):
        if control == 0: self.output = to_short(input_a + input_b)
        elif control == 1: self.output = to_short(input_a & input_b)
        elif control == 2: self.output = to_short(input_a)
        elif control == 3: self.output = to_short(~input_a)
        
        self.z_bit = self.output == 0
        self.n_bit = self.output < 0

class Shifter:
    def __init__(self):
        self.output = 0

    def shift(self, control, input_val):
        self.output = input_val
        if control == 1: self.output = to_short(input_val >> 1)
        elif control == 2: self.output = to_short(input_val << 1)

class MUX:
    def __init__(self):
        self.output = 0

    def decide_output(self, control, input_a, input_b):
        self.output = input_b if control else input_a

class MSL:
    def __init__(self):
        self.output = False

    def generate_output(self, control, n, z):
        self.output = (control == 3) or (control == 1 and n) or (control == 2 and z)

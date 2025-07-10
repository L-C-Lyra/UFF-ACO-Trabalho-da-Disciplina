# memory.py

from helpers import to_short

class Memory:
    read_counter = 0
    write_counter = 0
    
    def __init__(self):
        self.memory = [0] * 4096
        self.address = 0
        self.reset()

    def reset(self):
        for i in range(len(self.memory)):
            self.memory[i] = 0
        self.address = 0
        Memory.read_counter = 0
        Memory.write_counter = 0
        
    def is_read_ready(self):
        return Memory.read_counter == 2

    def is_write_ready(self):
        return Memory.write_counter == 2

    @staticmethod
    def increment_read_counter():
        Memory.read_counter += 1

    @staticmethod
    def increment_write_counter():
        Memory.write_counter += 1

    def read(self):
        Memory.read_counter = 0
        return self.memory[self.address]

    def write(self, value):
        Memory.write_counter = 0
        self.memory[self.address] = to_short(value)

    def set_address(self, address):
        self.address = address & 0x0FFF

    def pass_code(self, code):
        for i, line in enumerate(code):
            if i < len(self.memory):
                self.memory[i] = to_short(line)

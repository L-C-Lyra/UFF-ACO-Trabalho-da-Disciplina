# cpu.py

from helpers import REG_NAMES
from hardware_components import ALU, Shifter, Register, MUX
from memory import Memory
from control_unit import ControlUnit

class CPU:
    def __init__(self):
        self.alu = ALU()
        self.shifter = Shifter()
        self.amux = MUX()
        self.control_unit = ControlUnit()
        self.memory = Memory()

        self.registers = [Register(REG_NAMES[i], 0) for i in range(16)]
        self.registers[2].set_value(4096) # SP
        self.registers[6].set_value(1)    # +1
        self.registers[7].set_value(-1)   # -1
        self.registers[8].set_value(0x0FFF) # AMASK
        self.registers[9].set_value(0x00FF) # SMASK
        
        self.mbr = 0; self.mar = 0; self.a_latch = 0; self.b_latch = 0; self.count = 0

    def reset(self):
        self.__init__()
            
    def execute_datapath(self):
        if self.memory.is_read_ready(): self.mbr = self.memory.read()
        if self.memory.is_write_ready(): self.memory.write(self.mbr)
        self.a_latch = self.registers[self.control_unit.a_control].get_value()
        self.amux.decide_output(self.control_unit.amux_control, self.a_latch, self.mbr)
        a_input = self.amux.output
        self.b_latch = self.registers[self.control_unit.b_control].get_value()
        b_input = self.b_latch
        self.alu.calculate(self.control_unit.alu_control, a_input, b_input)
        self.control_unit.n_bit = self.alu.n_bit
        self.control_unit.z_bit = self.alu.z_bit
        self.shifter.shift(self.control_unit.sh_control, self.alu.output)
        if self.control_unit.mar_control: self.mar = self.b_latch & 0x0FFF
        if self.control_unit.mbr_control: self.mbr = self.shifter.output
        if self.control_unit.enc_control: self.registers[self.control_unit.c_control].set_value(self.shifter.output)
        if self.control_unit.rd_control: self.memory.set_address(self.mar)
        if self.control_unit.wr_control: self.memory.set_address(self.mar)
            
    def advance_subcycle(self):
        subcycle = (self.count % 4) + 1
        
        if subcycle == 1:
            self.control_unit.run_first_subcycle()
        elif subcycle == 2:
            self.control_unit.run_second_subcycle()
        elif subcycle == 3:
            self.control_unit.run_third_subcycle()
        elif subcycle == 4:
            self.control_unit.run_fourth_subcycle()

        self.execute_datapath()

        if subcycle == 4:
            self.control_unit.reset_signals()

        self.count += 1

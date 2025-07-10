# simulator.py 

import tkinter as tk
from tkinter import filedialog, messagebox
import time
from cpu import CPU
from assembler import Assembler
from helpers import to_short
from interface import SimulatorUI

# =============================================================================
# CLASSE PRINCIPAL DO SIMULADOR
# =============================================================================

class MIC1Simulator(tk.Tk):
    def __init__(self):
        super().__init__()
        self.cpu = CPU()
        self.assembler = Assembler()
        self.program_source = []
        self.is_auto_running = False
        self.auto_run_start_time = 0

        self.title("Simulador Educativo MIC-1")
        self.geometry("1800x950")
        
        self.ui = SimulatorUI(self)
        
        self._connect_controls()
        self.update_all_displays()

    def _connect_controls(self):
        panel = self.ui.code_control_panel
        panel.load_button.config(command=self.load_from_file)
        panel.assemble_button.config(command=self.load_program)
        panel.step_micro_button.config(command=self.next_micro)
        panel.step_macro_button.config(command=self.next_macro)
        panel.run_button.config(command=self.toggle_auto_run)
        panel.reset_button.config(command=self.clear_program)

    # =========================================================================
    # SEÇÃO DE LÓGICA DO SIMULADOR 
    # =========================================================================

    def load_from_file(self):
        filepath = filedialog.askopenfilename(filetypes=(("Text Files", "*.txt"), ("All files", "*.*")))
        if not filepath: return
        try:
            with open(filepath, "r", encoding='utf-8') as f: source_code = f.read()
            self.ui.code_control_panel.code_area.delete("1.0", tk.END)
            self.ui.code_control_panel.code_area.insert(tk.END, source_code)
            self.ui.status_var.set(f"Arquivo {filepath.split('/')[-1]} carregado.")
            messagebox.showinfo("Sucesso", f"Código carregado de {filepath.split('/')[-1]}.\nAgora clique em 'Montar e Carregar'.")
        except Exception as e:
            messagebox.showerror("Erro de Leitura", f"Não foi possível ler o arquivo:\n{e}")

    def load_program(self):
        source_code = self.ui.code_control_panel.code_area.get("1.0", tk.END)
        if not source_code.strip():
            messagebox.showwarning("Aviso", "A área de código está vazia.")
            return
        self.clear_program(reset_code_area=False)
        try:
            machine_code, asm_lines = self.assembler.assemble(source_code)
            self.cpu.memory.pass_code(machine_code)
            self.program_source = asm_lines
            self.ui.status_var.set("Programa montado e carregado na memória.")
            messagebox.showinfo("Sucesso", "Programa montado e carregado na memória!")
        except Exception as e:
            messagebox.showerror("Erro de Montagem", str(e))
            self.clear_program()
        self.update_all_displays()
    
    def clear_program(self, reset_code_area=True):
        if self.is_auto_running: self.toggle_auto_run()
        self.cpu.reset()
        self.program_source.clear()
        if reset_code_area: self.ui.code_control_panel.code_area.delete("1.0", tk.END)
        self.ui.status_var.set("Simulador resetado.")
        self.update_all_displays()

    def next_micro(self):
        if self.is_auto_running: return
        self.cpu.advance_subcycle()
        subcycle = self.cpu.count % 4
        self.ui.status_var.set(f"Microinstrução executada. Subciclo: {subcycle if subcycle != 0 else 4}/4.")
        self.update_all_displays()

    def next_macro(self):
        if not self.program_source: return
        
        if self.is_auto_running:
            pass

        start_mpc = self.cpu.control_unit.mpc
        if start_mpc == 0:
             self.cpu.advance_subcycle()
        
        while self.cpu.control_unit.mpc != 0:
            self.cpu.advance_subcycle()
        
        self.ui.status_var.set("Instrução Assembly (macro) executada.")
        self.update_all_displays()

    def toggle_auto_run(self):
        if not self.program_source:
            messagebox.showwarning("Aviso", "Nenhum programa carregado na memória.")
            return
        self.is_auto_running = not self.is_auto_running
        run_button = self.ui.code_control_panel.run_button
        if self.is_auto_running:
            run_button.config(text="Parar Execução")
            self.ui.status_var.set("Executando automaticamente...")
            self.auto_run_start_time = time.monotonic()
            self.auto_step()
        else:
            duration = time.monotonic() - self.auto_run_start_time
            self.ui.status_var.set("Execução parada pelo usuário.")
            messagebox.showinfo("Parado", f"Execução parada pelo usuário.\nDuração: {duration:.2f} segundos.")
            run_button.config(text="Executar (Run)")

    def auto_step(self):
        if not self.is_auto_running:
            self.ui.code_control_panel.run_button.config(text="Executar (Run)")
            return
        
        pc = self.cpu.registers[0].get_value()
        is_halted = (0 <= pc < len(self.program_source)) and self.program_source[pc].strip().upper().startswith("HALT")

        if not (0 <= pc < len(self.program_source)) or is_halted:
            duration = time.monotonic() - self.auto_run_start_time
            self.is_auto_running = False
            self.ui.code_control_panel.run_button.config(text="Executar (Run)")
            self.update_all_displays()
            message = f"Execução terminada (instrução HALT no endereço {pc})." if is_halted else f"Execução terminada: PC ({pc}) fora do alcance do programa."
            self.ui.status_var.set(f"Fim da execução. PC: {pc}.")
            messagebox.showinfo("Fim", f"{message}\nDuração: {duration:.2f} segundos.")
            return

        self.next_macro()
        
        delay = int(self.ui.code_control_panel.speed_scale.get())
        self.after(delay, self.auto_step)

    # =========================================================================
    # SEÇÃO DE ATUALIZAÇÃO DA INTERFACE (VISUAL)
    # =========================================================================

    def update_all_displays(self):
        # A. Datapath
        self.ui.datapath_canvas.update_datapath(self.cpu)

        # B. Registradores
        self._update_register_displays()

        # C. Memória Principal (RAM)
        self._update_ram_display()

        # D. Memória de Controle (Microcódigo)
        self._update_ucode_display()
        
        self.update_idletasks()

    def _update_register_displays(self):
        cu = self.cpu.control_unit
        
        reg_map = {r.name: r.get_value() for r in self.cpu.registers}
        reg_map["MAR"] = self.cpu.mar
        reg_map["MBR"] = self.cpu.mbr
        
        for name, display in self.ui.register_displays.items():
            val = reg_map.get(name, 0)
            display.update(f"0x{val & 0xFFFF:04X}", to_short(val))

            is_c_bus_target = cu.enc_control and self.cpu.registers[cu.c_control].name == name
            is_b_bus_source = self.cpu.registers[cu.b_control].name == name
            is_a_bus_source = self.cpu.registers[cu.a_control].name == name and not cu.amux_control
            
            if name == "MBR" and cu.mbr_control:
                 display.highlight_write()
            elif name == "MAR" and cu.mar_control:
                 display.highlight_write()
            elif is_c_bus_target:
                 display.highlight_write()
            elif is_b_bus_source or is_a_bus_source:
                 display.highlight_read()

    def _update_ram_display(self):
        ram_text = self.ui.ram_text
        ram_text.config(state=tk.NORMAL)
        ram_text.delete("1.0", tk.END)

        pc = self.cpu.registers[0].get_value()
        mar = self.cpu.mar
        sp = self.cpu.registers[2].get_value()
        
        start_addr = max(0, pc - 20)
        end_addr = min(len(self.cpu.memory.memory), start_addr + 60)

        for i in range(start_addr, end_addr):
            val = self.cpu.memory.memory[i]
            line_str = f"{i:04X}:  {val & 0xFFFF:04X}  ({to_short(val):<6}) "
            if i < len(self.program_source):
                line_str += f"  ; {self.program_source[i]}"
            
            line_num = i - start_addr + 1
            ram_text.insert(tk.END, line_str + "\n")
            
            tags_to_add = []
            if i == pc: tags_to_add.append("pc")
            if i == mar: tags_to_add.append("mar")
            if i == sp: tags_to_add.append("sp")

            for tag in tags_to_add:
                ram_text.tag_add(tag, f"{line_num}.0", f"{line_num}.end")

        ram_text.config(state=tk.DISABLED)

    def _update_ucode_display(self):
        ucode_text = self.ui.ucode_text
        mpc = self.cpu.control_unit.mpc
        
        self.ui.mpc_label.config(text=f"MPC: {mpc} (0x{mpc:03X})")
        ucode_text.config(state=tk.NORMAL)
        ucode_text.delete("1.0", tk.END)

        for i, line in enumerate(self.cpu.control_unit.control_memory):
            ucode_text.insert(tk.END, f"{i:03X}: {line}\n")
            if i == mpc:
                ucode_text.tag_add("highlight", f"{i+1}.0", f"{i+1}.end")
        
        ucode_text.see(f"{max(1, mpc - 5)}.0")
        ucode_text.config(state=tk.DISABLED)

if __name__ == "__main__":
    app = MIC1Simulator()
    app.mainloop()

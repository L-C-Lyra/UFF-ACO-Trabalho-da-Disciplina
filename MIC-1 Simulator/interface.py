import tkinter as tk
from tkinter import ttk, scrolledtext, Canvas
from helpers import REG_NAMES, ALU_OPS, SH_OPS

class Theme:
    BACKGROUND = "#212121"
    WIDGET_BG = "#37474F"
    ACCENT_BG = "#455A64"
    TEXT_NORMAL = "#e0e0e0"
    TEXT_MUTED = "#9E9E9E"
    PRIMARY_ACCENT = "#4FC3F7"
    LINE_COLOR = "#546E7A"
    ACTIVE_BUS = "#EF5350"
    ACTIVE_SIGNAL = "#FFCA28"
    READ_HIGHLIGHT = "#0288D1"
    WRITE_HIGHLIGHT = "#FFCA28"
    TEXT_ON_WRITE = "black"
    PC_HIGHLIGHT = "#D32F2F"
    MAR_HIGHLIGHT = "#FFCA28"
    SP_HIGHLIGHT = "#7B1FA2"
    UCODE_HIGHLIGHT = "#00796B"

    FONT_TITLE = ("Segoe UI", 12, "bold")
    FONT_SUBTITLE = ("Segoe UI", 11)
    FONT_BODY = ("Segoe UI", 10, "bold")
    FONT_CODE = ("Consolas", 11)
    FONT_CODE_SMALL = ("Consolas", 10)
    FONT_VAL = ("Consolas", 11, "bold")
    FONT_DISPLAY_BIG = ("Consolas", 18, "bold")
    FONT_DISPLAY_SMALL = ("Consolas", 11)
    FONT_SIGNAL = ("Segoe UI", 9, "italic")

class RegisterDisplay(ttk.Frame):
    def __init__(self, parent, reg_name, **kwargs):
        super().__init__(parent, padding=10, style='TFrame', **kwargs)
        self.config(borderwidth=1, relief="solid")
        
        self.name_label = ttk.Label(self, text=reg_name, font=Theme.FONT_TITLE, foreground=Theme.PRIMARY_ACCENT)
        self.name_label.pack(fill="x")

        self.hex_val_label = ttk.Label(self, text="0x0000", font=Theme.FONT_DISPLAY_BIG)
        self.hex_val_label.pack(pady=5, fill="x")
        
        self.dec_val_label = ttk.Label(self, text="(0)", font=Theme.FONT_DISPLAY_SMALL, foreground=Theme.TEXT_MUTED)
        self.dec_val_label.pack(fill="x")

    def update(self, hex_val, dec_val):
        self.hex_val_label.config(text=hex_val)
        self.dec_val_label.config(text=f"({dec_val})")
        self.name_label.config(background=Theme.WIDGET_BG, foreground=Theme.PRIMARY_ACCENT)
        self.hex_val_label.config(background=Theme.WIDGET_BG, foreground=Theme.TEXT_NORMAL)
        self.dec_val_label.config(background=Theme.WIDGET_BG, foreground=Theme.TEXT_MUTED)

    def highlight_read(self):
        self.name_label.config(background=Theme.READ_HIGHLIGHT, foreground=Theme.TEXT_NORMAL)
        self.hex_val_label.config(background=Theme.READ_HIGHLIGHT, foreground=Theme.TEXT_NORMAL)
        self.dec_val_label.config(background=Theme.READ_HIGHLIGHT, foreground=Theme.TEXT_NORMAL)

    def highlight_write(self):
        self.name_label.config(background=Theme.WRITE_HIGHLIGHT, foreground=Theme.TEXT_ON_WRITE)
        self.hex_val_label.config(background=Theme.WRITE_HIGHLIGHT, foreground=Theme.TEXT_ON_WRITE)
        self.dec_val_label.config(background=Theme.WRITE_HIGHLIGHT, foreground=Theme.TEXT_ON_WRITE)

class AppStyle(ttk.Style):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.theme_use('clam')
        # Configurações globais
        self.configure('.', background=Theme.BACKGROUND, foreground=Theme.TEXT_NORMAL, bordercolor=Theme.LINE_COLOR, font=Theme.FONT_BODY)
        # Estilos específicos
        self.configure('TFrame', background=Theme.BACKGROUND)
        self.configure('TLabel', background=Theme.WIDGET_BG, foreground=Theme.TEXT_NORMAL)
        self.configure('TNotebook', background=Theme.BACKGROUND, bordercolor=Theme.BACKGROUND)
        self.configure('TNotebook.Tab', background=Theme.ACCENT_BG, foreground=Theme.TEXT_NORMAL, padding=[10, 5], font=Theme.FONT_BODY)
        self.map('TNotebook.Tab', background=[('selected', Theme.PRIMARY_ACCENT), ('active', Theme.ACCENT_BG)], foreground=[('selected', 'black')])
        self.configure('TLabelframe', background=Theme.BACKGROUND, bordercolor=Theme.LINE_COLOR, padding=10)
        self.configure('TLabelframe.Label', background=Theme.BACKGROUND, foreground=Theme.PRIMARY_ACCENT, font=Theme.FONT_TITLE)
        self.configure('TButton', background=Theme.ACCENT_BG, foreground=Theme.TEXT_NORMAL, borderwidth=1, focusthickness=3, focuscolor=Theme.ACCENT_BG)
        self.map('TButton', background=[('active', Theme.LINE_COLOR)])
        self.configure('Danger.TButton', foreground='#FFAB91')
        self.map('Danger.TButton', background=[('active', '#D84315')])

class CodeControlPanel(ttk.Frame):
    def __init__(self, parent, **kwargs):
        super().__init__(parent, style='TFrame', **kwargs)
        self._create_widgets()

    def _create_widgets(self):
        code_frame = ttk.LabelFrame(self, text="1. Código Assembly")
        code_frame.pack(fill="both", expand=True, pady=5)
        self.code_area = scrolledtext.ScrolledText(code_frame, height=15, font=Theme.FONT_CODE, wrap=tk.WORD, bg="#263238", fg="#B0BEC5", insertbackground="white")
        self.code_area.pack(fill="both", expand=True, pady=5, padx=5)
        
        asm_buttons = ttk.Frame(code_frame, style='TFrame')
        asm_buttons.pack(fill="x", pady=5, padx=5)
        self.load_button = ttk.Button(asm_buttons, text="Carregar Arquivo")
        self.load_button.pack(side=tk.LEFT, expand=True, fill="x", padx=(0,2))
        self.assemble_button = ttk.Button(asm_buttons, text="Montar e Carregar")
        self.assemble_button.pack(side=tk.LEFT, expand=True, fill="x", padx=(2,0))

        control_frame = ttk.LabelFrame(self, text="2. Controle da Execução")
        control_frame.pack(fill="x", pady=5)
        self.step_micro_button = ttk.Button(control_frame, text="Avançar Microinstrução (Step)")
        self.step_micro_button.pack(fill="x", pady=2, padx=5)
        self.step_macro_button = ttk.Button(control_frame, text="Avançar Instrução (Macro)")
        self.step_macro_button.pack(fill="x", pady=2, padx=5)
        self.run_button = ttk.Button(control_frame, text="Executar (Run)")
        self.run_button.pack(fill="x", pady=2, padx=5)
        
        speed_frame = ttk.Frame(control_frame, style='TFrame')
        speed_frame.pack(fill="x", pady=10, padx=10)
        ttk.Label(speed_frame, text="Velocidade (ms):").pack(side=tk.LEFT)
        self.speed_scale = ttk.Scale(speed_frame, from_=1, to=500, orient=tk.HORIZONTAL)
        self.speed_scale.set(50)
        self.speed_scale.pack(side=tk.LEFT, fill="x", expand=True, padx=5)
        
        self.reset_button = ttk.Button(control_frame, text="Resetar Simulador", style="Danger.TButton")
        self.reset_button.pack(fill="x", side=tk.BOTTOM, pady=10, padx=5)

class DatapathCanvas(Canvas):
    def __init__(self, parent, **kwargs):
        super().__init__(parent, bg=Theme.BACKGROUND, highlightthickness=0, **kwargs)
        self.drawn = False

    def draw(self, w, h):
        self.delete("all")
        if w < 10 or h < 10: return
        PAD = 20
        regs_y, regs_h = h * 0.05, h * 0.15
        bus_y1, bus_y2 = regs_y + regs_h + PAD, h - PAD - (PAD * 3)
        bus_b_x, bus_a_x = w * 0.25, w * 0.75
        alu_y, alu_h = h * 0.45, h * 0.15
        shifter_y, shifter_h = alu_y + alu_h + PAD, h * 0.1
        c_bus_y = shifter_y + shifter_h + PAD
        mem_x, mem_y, mem_w = w * 0.02, h * 0.45, w * 0.18

        def draw_comp(x, y, width, height, text, tag):
            self.create_rectangle(x, y, x + width, y + height, fill=Theme.WIDGET_BG, outline=Theme.LINE_COLOR, width=1.5, tags=tag)
            self.create_text(x + width / 2, y + PAD, text=text, fill=Theme.PRIMARY_ACCENT, font=Theme.FONT_TITLE, tags=f"{tag}_text")

        draw_comp(w*0.1, regs_y, w*0.8, regs_h, "Bloco de Registradores", "regs")
        draw_comp(w*0.35, alu_y, w*0.3, alu_h, "ULA", "alu")
        self.create_text(w/2, alu_y + PAD*2.5, text="...", font=Theme.FONT_DISPLAY_BIG, fill=Theme.TEXT_NORMAL, tags="alu_op_text")
        draw_comp(w*0.35, shifter_y, w*0.3, shifter_h, "Shifter", "shifter")
        draw_comp(bus_a_x - 50, alu_y - 60, 100, 40, "MUX A", "mux_a")
        draw_comp(mem_x, mem_y, mem_w, h*0.35, "RAM", "ram")
        draw_comp(mem_x + PAD, mem_y + 50, w*0.1, 40, "MAR", "mar_box")
        draw_comp(mem_x + PAD, mem_y + 110, w*0.1, 40, "MBR", "mbr_box")

        for bus, x_pos, tag_l, tag_v in [('B', bus_b_x, "bus_b_line", "bus_b_val"), ('A', bus_a_x, "bus_a_line", "bus_a_val")]:
            self.create_line(x_pos, bus_y1, x_pos, bus_y2, width=5, fill=Theme.LINE_COLOR, tags=tag_l)
            self.create_text(x_pos, bus_y1 - PAD, text=f"Barramento {bus}", fill=Theme.TEXT_NORMAL, font=Theme.FONT_TITLE)
            self.create_text(x_pos, bus_y1 - 3, text="0x0000", fill=Theme.ACTIVE_BUS, font=Theme.FONT_VAL, tags=tag_v)
        self.create_line(w * 0.05, c_bus_y, w * 0.95, c_bus_y, width=5, fill=Theme.LINE_COLOR, tags="bus_c_line")
        self.create_text(w/2, c_bus_y - PAD, text="Barramento C", fill=Theme.TEXT_NORMAL, font=Theme.FONT_TITLE)
        self.create_text(w/2, c_bus_y - 3, text="0x0000", fill=Theme.ACTIVE_BUS, font=Theme.FONT_VAL, tags="bus_c_val")
        
        self.create_line(bus_b_x, regs_y+regs_h, bus_b_x, bus_y1, width=2, fill=Theme.LINE_COLOR, arrow=tk.LAST, tags="regs_to_b")
        self.create_line(bus_a_x, regs_y+regs_h, bus_a_x, alu_y - 60, width=2, fill=Theme.LINE_COLOR, arrow=tk.LAST, tags="regs_to_amux")
        self.create_line(bus_a_x - 50, alu_y - 40, bus_a_x, alu_y - 40, bus_a_x, alu_y, width=2, fill=Theme.LINE_COLOR, arrow=tk.LAST, tags="amux_to_a")
        self.create_line(bus_b_x, alu_y+alu_h/2, w*0.35, alu_y+alu_h/2, width=2, fill=Theme.LINE_COLOR, arrow=tk.LAST, tags="b_to_alu")
        self.create_line(bus_a_x, alu_y+alu_h/2, w*0.65, alu_y+alu_h/2, width=2, fill=Theme.LINE_COLOR, arrow=tk.LAST, tags="a_to_alu")
        self.create_line(w*0.35+w*0.3/2, alu_y+alu_h, w*0.35+w*0.3/2, shifter_y, width=2, fill=Theme.LINE_COLOR, arrow=tk.LAST, tags="alu_to_shifter")
        self.create_line(w*0.35+w*0.3/2, shifter_y+shifter_h, w*0.35+w*0.3/2, c_bus_y, width=2, fill=Theme.LINE_COLOR, arrow=tk.LAST, tags="shifter_to_c")
        self.create_line(w*0.9, c_bus_y, w*0.9, regs_y+regs_h, width=2, fill=Theme.LINE_COLOR, arrow=tk.FIRST, tags="c_to_regs")
        self.create_line(bus_b_x, mem_y+70, mem_x+mem_w, mem_y+70, width=2, fill=Theme.LINE_COLOR, arrow=tk.FIRST, tags="b_to_mar")
        self.create_line(mem_x+mem_w, mem_y+130, bus_a_x - 70, mem_y+130, bus_a_x - 70, alu_y-60, width=2, fill=Theme.LINE_COLOR, arrow=tk.LAST, tags="mbr_to_amux")
        
        def draw_signal(x, y, text, tag): self.create_oval(x-5, y-5, x+5, y+5, fill=Theme.LINE_COLOR, outline=Theme.LINE_COLOR, tags=f"{tag}_led"); self.create_text(x+8, y, text=text, fill=Theme.TEXT_MUTED, font=Theme.FONT_SIGNAL, anchor="w", tags=tag)
        draw_signal(bus_a_x - 45, alu_y-70, "AMUX", "sig_amux")
        draw_signal(w*0.9, c_bus_y + 15, "ENC", "sig_enc")
        draw_signal(mem_x - 10, mem_y+PAD, "RD", "sig_rd")
        draw_signal(mem_x - 10, mem_y+PAD*2, "WR", "sig_wr")
        self.drawn = True
        
    def update_datapath(self, cpu):
        if not self.drawn: return
        cu = cpu.control_unit

        self.itemconfig("bus_b_val", text=f"0x{cpu.b_latch & 0xFFFF:04X}")
        self.itemconfig("bus_a_val", text=f"0x{cpu.amux.output & 0xFFFF:04X}")
        self.itemconfig("bus_c_val", text=f"0x{cpu.shifter.output & 0xFFFF:04X}")

        self.itemconfig("bus_b_line", fill=Theme.ACTIVE_BUS if cu.b_control != 5 else Theme.LINE_COLOR)
        self.itemconfig("bus_a_line", fill=Theme.ACTIVE_BUS if cu.a_control != 5 else Theme.LINE_COLOR)
        self.itemconfig("bus_c_line", fill=Theme.ACTIVE_BUS if cu.enc_control else Theme.LINE_COLOR)

        self.itemconfig("regs_to_b", fill=Theme.ACTIVE_BUS if cu.b_control != 5 else Theme.LINE_COLOR)
        self.itemconfig("regs_to_amux", fill=Theme.ACTIVE_BUS if not cu.amux_control else Theme.LINE_COLOR)
        self.itemconfig("mbr_to_amux", fill=Theme.ACTIVE_BUS if cu.amux_control else Theme.LINE_COLOR)
        self.itemconfig("c_to_regs", fill=Theme.ACTIVE_BUS if cu.enc_control else Theme.LINE_COLOR)
        
        self.itemconfig("sig_amux_led", fill=Theme.ACTIVE_SIGNAL if cu.amux_control else Theme.LINE_COLOR)
        self.itemconfig("sig_enc_led", fill=Theme.ACTIVE_SIGNAL if cu.enc_control else Theme.LINE_COLOR)
        self.itemconfig("sig_rd_led", fill=Theme.ACTIVE_SIGNAL if cu.rd_control else Theme.LINE_COLOR)
        self.itemconfig("sig_wr_led", fill=Theme.ACTIVE_SIGNAL if cu.wr_control else Theme.LINE_COLOR)

        self.itemconfig("mar_box", outline=Theme.ACTIVE_BUS if cu.mar_control else Theme.LINE_COLOR)
        self.itemconfig("alu", outline=Theme.ACTIVE_SIGNAL if cu.alu_control != 2 else Theme.LINE_COLOR) # ULA ativa se não for só 'passA'
        
        alu_op = ALU_OPS.get(cu.alu_control, "?")
        sh_op = SH_OPS.get(cu.sh_control, "")
        self.itemconfig("alu_op_text", text=f"{alu_op} {sh_op}")

class SimulatorUI:
    def __init__(self, root):
        self.root = root
        AppStyle(self.root)
        self._create_main_layout()
        self._create_widgets()

    def _create_main_layout(self):
        self.main_pane = ttk.PanedWindow(self.root, orient=tk.HORIZONTAL)
        self.main_pane.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        self.left_panel = ttk.Frame(self.main_pane, style='TFrame')
        self.main_pane.add(self.left_panel, weight=2)
        
        self.center_notebook = ttk.Notebook(self.main_pane)
        self.main_pane.add(self.center_notebook, weight=5)
        
        self.status_var = tk.StringVar(value="Interface Pronta")
        status_bar = ttk.Label(self.root, textvariable=self.status_var, relief=tk.SUNKEN, anchor='w', padding=5, font=Theme.FONT_CODE_SMALL)
        status_bar.pack(side=tk.BOTTOM, fill=tk.X)

    def _create_widgets(self):
        self.code_control_panel = CodeControlPanel(self.left_panel)
        self.code_control_panel.pack(fill="both", expand=True)

        datapath_tab = ttk.Frame(self.center_notebook, style='TFrame')
        self.datapath_canvas = DatapathCanvas(datapath_tab)
        self.datapath_canvas.pack(fill="both", expand=True)
        self.datapath_canvas.bind("<Configure>", lambda e: self.datapath_canvas.draw(e.width, e.height))
        self.center_notebook.add(datapath_tab, text=" Datapath ")

        regs_tab = ttk.Frame(self.center_notebook, style='TFrame')
        regs_frame = ttk.Frame(regs_tab, padding=20)
        regs_frame.pack(fill="both", expand=True)
        
        main_regs = ["PC", "AC", "SP", "IR", "MAR", "MBR"]
        self.register_displays = {name: RegisterDisplay(regs_frame, name) for name in main_regs}
        
        for i, (name, display) in enumerate(self.register_displays.items()):
            col, row = i % 3, i // 3
            display.grid(row=row, column=col, padx=10, pady=10, sticky="nsew")
            regs_frame.grid_columnconfigure(col, weight=1); regs_frame.grid_rowconfigure(row, weight=1)
        self.center_notebook.add(regs_tab, text=" Registradores ")

        ram_tab = ttk.Frame(self.center_notebook, style='TFrame')
        self.ram_text = scrolledtext.ScrolledText(ram_tab, font=Theme.FONT_CODE, wrap=tk.NONE, bg="#263238", fg="#B0BEC5", insertbackground="white")
        self.ram_text.pack(fill="both", expand=True, padx=5, pady=5)
        self.ram_text.tag_config("pc", background=Theme.PC_HIGHLIGHT, foreground="black")
        self.ram_text.tag_config("mar", background=Theme.MAR_HIGHLIGHT, foreground="black")
        self.ram_text.tag_config("sp", background=Theme.SP_HIGHLIGHT, foreground="black")
        self.center_notebook.add(ram_tab, text=" Memória Principal (RAM) ")

        ucode_tab = ttk.Frame(self.center_notebook, style='TFrame')
        self.mpc_label = ttk.Label(ucode_tab, text="MPC: 0", font=("Consolas", 11, "bold"), foreground=Theme.PRIMARY_ACCENT, padding=(10,5))
        self.mpc_label.pack(anchor="w", fill="x")
        self.ucode_text = scrolledtext.ScrolledText(ucode_tab, font=Theme.FONT_CODE_SMALL, wrap=tk.NONE, bg="#263238", fg="#B0BEC5")
        self.ucode_text.pack(fill="both", expand=True, padx=5, pady=5)
        self.ucode_text.tag_config("highlight", background=Theme.UCODE_HIGHLIGHT, foreground='white')
        self.center_notebook.add(ucode_tab, text=" Memória de Controle ")

if __name__ == '__main__':
    root = tk.Tk()
    root.title("Demonstração do UI Kit Profissional")
    root.geometry("1800x950")
    root.configure(bg=Theme.BACKGROUND)
    
    ui = SimulatorUI(root)

    ui.code_control_panel.load_button.config(command=lambda: print("Botão 'Carregar' foi pressionado."))
    ui.status_var.set("UI Kit carregado com sucesso. Nenhum back-end conectado.")
    
    demo_reg = ui.register_displays["AC"]
    demo_reg.update("0x1234", 4660)
    demo_reg.highlight_read()
    
    ui.register_displays["PC"].highlight_write()

    root.mainloop()

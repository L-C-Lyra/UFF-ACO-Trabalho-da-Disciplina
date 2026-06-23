package dev.flaffy.horsepoweremu.emu;

public class CPU {

    public final int[] R = new int[8];
    public int PC, SP, ST;
    public boolean halted;
    public long totalCycles, totalInstructions;
    public String lastMnem = "---";
    public int    lastPC;

    public volatile boolean irqLine;
    public volatile boolean nmiPending;

    private Memory mem;

    public static final int F_C = 0x01, F_Z = 0x02, F_I = 0x04;
    public static final int F_E = 0x20, F_V = 0x40, F_N = 0x80;

    public static final int A0 = 6, A1 = 7;

    private static final int M_IMM=0, M_ABS=1, M_ABA0=2, M_ABA1=3;
    private static final int M_IND=4, M_IDA0=5, M_SPR=6, M_IIX=7;

    private static final String[] OPNAMES = new String[256];
    static {
        for (int i = 0; i < 256; i++) OPNAMES[i] = "???";
        OPNAMES[0x00]="NOP"; OPNAMES[0x01]="HALT"; OPNAMES[0x02]="FLG";
        OPNAMES[0x03]="RETI"; OPNAMES[0x04]="SWI";
        OPNAMES[0x10]="LOD"; OPNAMES[0x11]="STO"; OPNAMES[0x12]="LEA"; OPNAMES[0x13]="SWP";
        OPNAMES[0x20]="ADD"; OPNAMES[0x21]="SUB"; OPNAMES[0x22]="CMP"; OPNAMES[0x23]="INC";
        OPNAMES[0x24]="DEC"; OPNAMES[0x25]="MUL"; OPNAMES[0x26]="DIV"; OPNAMES[0x27]="TST";
        OPNAMES[0x28]="ADDR"; OPNAMES[0x29]="SUBR"; OPNAMES[0x2A]="CMPR"; OPNAMES[0x2B]="MULR";
        OPNAMES[0x2C]="DIVR";
        OPNAMES[0x30]="AND"; OPNAMES[0x31]="OR"; OPNAMES[0x32]="EOR"; OPNAMES[0x33]="NOT";
        OPNAMES[0x34]="NEG"; OPNAMES[0x35]="ANDR"; OPNAMES[0x36]="ORR"; OPNAMES[0x37]="EORR";
        OPNAMES[0x40]="SHF"; OPNAMES[0x41]="ROT";
        OPNAMES[0x50]="JMP"; OPNAMES[0x51]="CALL"; OPNAMES[0x52]="RET";
        OPNAMES[0x53]="BCC"; OPNAMES[0x54]="BCS"; OPNAMES[0x55]="BEQ"; OPNAMES[0x56]="BNE";
        OPNAMES[0x57]="BMI"; OPNAMES[0x58]="BPL"; OPNAMES[0x59]="BVC"; OPNAMES[0x5A]="BVS";
        OPNAMES[0x5B]="BES"; OPNAMES[0x5C]="BEC";
        OPNAMES[0x60]="PSH"; OPNAMES[0x61]="PLL"; OPNAMES[0x62]="MOV"; OPNAMES[0x63]="SPT";
    }

    public void setMemory(Memory m) { this.mem = m; }

    public void reset() {
        for (int i = 0; i < 8; i++) R[i] = 0;
        SP = 0xFEFF;
        ST = F_I;
        halted = false;
        irqLine = false;
        nmiPending = false;
        totalCycles = totalInstructions = 0;
        lastMnem = "RESET";
        lastPC   = 0;
        PC = mem != null ? mem.read(0x0000) : 0;
    }

    public int step() {
        if (nmiPending) { nmiPending = false; halted = false; enterInterrupt(0x0002); return 8; }
        if (irqLine && (ST & F_I) == 0) { halted = false; enterInterrupt(0x0001); return 8; }
        if (halted) return 1;

        lastPC = PC;
        int w1 = read(PC);
        PC = (PC + 1) & 0xFFFF;

        int opcode = (w1 >> 8) & 0xFF;
        int fb = w1 & 0xFF;
        lastMnem = OPNAMES[opcode];
        int cycles = 4;

        int dst  = (fb >> 5) & 7;
        int mode = (fb >> 2) & 7;
        int src3 = (fb >> 2) & 7;
        int sub  = fb & 3;

        switch (opcode) {
            case 0x00:
                cycles = 2;
                break;
            case 0x01:
                halted = true;
                cycles = 2;
                break;
            case 0x02: {
                int op = (fb >> 7) & 1;
                int f = (fb >> 4) & 7;
                int bit = flagBit(f);
                if (op == 1) ST |= bit; else ST &= ~bit;
                ST &= 0xFF;
                cycles = 2;
                break;
            }
            case 0x03:
                ST = pull() & 0xFF;
                PC = pull();
                cycles = 6;
                break;
            case 0x04: {
                int vec = fb & 0xFF;
                enterInterrupt(0x0000 + vec);
                cycles = 8;
                break;
            }
            case 0x10: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                setReg(dst, srcVal(mode, w2));
                cycles = 6;
                break;
            }
            case 0x11: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                if (mode != M_IMM) write(eaFor(mode, w2), getReg(dst));
                cycles = 6;
                break;
            }
            case 0x12: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                if (mode != M_IMM) setReg(dst, eaFor(mode, w2));
                cycles = 4;
                break;
            }
            case 0x13: {
                int t = R[dst]; R[dst] = R[src3]; R[src3] = t;
                cycles = 4;
                break;
            }
            case 0x20: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                addOp(dst, srcVal(mode, w2), sub == 1);
                cycles = 6;
                break;
            }
            case 0x21: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                subOp(dst, srcVal(mode, w2), sub == 1);
                cycles = 6;
                break;
            }
            case 0x22: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                cmpOp(getReg(dst), srcVal(mode, w2));
                cycles = 6;
                break;
            }
            case 0x23: {
                int v = (getReg(dst) + 1) & 0xFFFF;
                setReg(dst, v);
                setNZ(v);
                cycles = 2;
                break;
            }
            case 0x24: {
                int v = (getReg(dst) - 1) & 0xFFFF;
                setReg(dst, v);
                setNZ(v);
                cycles = 2;
                break;
            }
            case 0x25: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                mulOp(dst, srcVal(mode, w2), sub == 1);
                cycles = 20;
                break;
            }
            case 0x26: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                divOp(dst, srcVal(mode, w2), sub);
                cycles = 30;
                break;
            }
            case 0x27:
                setNZ(getReg(dst));
                cycles = 2;
                break;
            case 0x28:
                addOp(dst, R[src3], sub == 1);
                cycles = 4;
                break;
            case 0x29:
                subOp(dst, R[src3], sub == 1);
                cycles = 4;
                break;
            case 0x2A:
                cmpOp(R[dst], R[src3]);
                cycles = 4;
                break;
            case 0x2B:
                mulOp(dst, R[src3], sub == 1);
                cycles = 18;
                break;
            case 0x2C:
                divOp(dst, R[src3], sub);
                cycles = 28;
                break;
            case 0x30: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                int v = getReg(dst) & srcVal(mode, w2);
                setReg(dst, v); setNZ(v);
                cycles = 6;
                break;
            }
            case 0x31: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                int v = getReg(dst) | srcVal(mode, w2);
                setReg(dst, v); setNZ(v);
                cycles = 6;
                break;
            }
            case 0x32: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                int v = getReg(dst) ^ srcVal(mode, w2);
                setReg(dst, v); setNZ(v);
                cycles = 6;
                break;
            }
            case 0x33: {
                int v = (~getReg(dst)) & 0xFFFF;
                setReg(dst, v); setNZ(v);
                cycles = 2;
                break;
            }
            case 0x34: {
                int old = getReg(dst);
                int v = (-old) & 0xFFFF;
                setReg(dst, v);
                ST &= ~(F_N | F_V | F_Z | F_C);
                if (old == 0x8000) ST |= F_V;
                if (old != 0) ST |= F_C;
                if (v == 0) ST |= F_Z;
                if ((v & 0x8000) != 0) ST |= F_N;
                cycles = 2;
                break;
            }
            case 0x35: {
                int v = R[dst] & R[src3];
                setReg(dst, v); setNZ(v);
                cycles = 4;
                break;
            }
            case 0x36: {
                int v = R[dst] | R[src3];
                setReg(dst, v); setNZ(v);
                cycles = 4;
                break;
            }
            case 0x37: {
                int v = R[dst] ^ R[src3];
                setReg(dst, v); setNZ(v);
                cycles = 4;
                break;
            }
            case 0x40: {
                int cnt = (fb >> 2) & 7; if (cnt == 0) cnt = 8;
                shfOp(dst, sub, cnt);
                cycles = 4;
                break;
            }
            case 0x41: {
                int cnt = (fb >> 2) & 7; if (cnt == 0) cnt = 8;
                rotOp(dst, sub, cnt);
                cycles = 4;
                break;
            }
            case 0x50: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                if (mode != M_IMM) PC = eaFor(mode, w2);
                cycles = 4;
                break;
            }
            case 0x51: {
                int w2 = read(PC); PC = (PC + 1) & 0xFFFF;
                if (mode != M_IMM) { push(PC); PC = eaFor(mode, w2); }
                cycles = 6;
                break;
            }
            case 0x52:
                PC = pull();
                cycles = 4;
                break;
            case 0x53: case 0x54: case 0x55: case 0x56: case 0x57: case 0x58:
            case 0x59: case 0x5A: case 0x5B: case 0x5C: {
                int off = fb & 0xFF;
                boolean t = branchCond(opcode);
                if (t) PC = (PC + (off >= 0x80 ? off - 0x100 : off)) & 0xFFFF;
                cycles = 3;
                break;
            }
            case 0x60:
                push(getReg(dst));
                cycles = 3;
                break;
            case 0x61: {
                int v = pull();
                setReg(dst, v);
                cycles = 4;
                break;
            }
            case 0x62:
                R[dst] = R[src3];
                cycles = 3;
                break;
            case 0x63:
                if (sub == 1) setReg(dst, SP);
                else if (sub == 2) SP = getReg(dst);
                cycles = 3;
                break;
            default:
                cycles = 2;
        }

        totalCycles += cycles;
        totalInstructions++;
        return cycles;
    }

    private void enterInterrupt(int vectorAddr) {
        push(PC);
        push(ST);
        ST |= F_I;
        ST &= 0xFF;
        PC = read(vectorAddr);
    }

    private int flagBit(int f) {
        switch (f) {
            case 0: return F_C;
            case 1: return F_Z;
            case 2: return F_I;
            case 3: return F_N;
            case 4: return F_V;
            case 5: return F_E;
            default: return 0;
        }
    }

    private boolean branchCond(int opcode) {
        switch (opcode) {
            case 0x53: return (ST & F_C) == 0;
            case 0x54: return (ST & F_C) != 0;
            case 0x55: return (ST & F_Z) != 0;
            case 0x56: return (ST & F_Z) == 0;
            case 0x57: return (ST & F_N) != 0;
            case 0x58: return (ST & F_N) == 0;
            case 0x59: return (ST & F_V) == 0;
            case 0x5A: return (ST & F_V) != 0;
            case 0x5B: return (ST & F_E) != 0;
            case 0x5C: return (ST & F_E) == 0;
            default:   return false;
        }
    }

    private int getReg(int idx) { return R[idx] & 0xFFFF; }

    private void setReg(int idx, int val) { R[idx] = val & 0xFFFF; }

    private int eaFor(int mode, int w2) {
        switch (mode) {
            case M_ABS:  return w2 & 0xFFFF;
            case M_ABA0: return (w2 + R[A0]) & 0xFFFF;
            case M_ABA1: return (w2 + R[A1]) & 0xFFFF;
            case M_IND:  return read(w2) & 0xFFFF;
            case M_IDA0: return read((w2 + R[A0]) & 0xFFFF) & 0xFFFF;
            case M_SPR:  return (SP + w2) & 0xFFFF;
            case M_IIX:  return (read((w2 + R[A0]) & 0xFFFF) + R[A1]) & 0xFFFF;
            default:     return w2 & 0xFFFF;
        }
    }

    private int srcVal(int mode, int w2) {
        if (mode == M_IMM) return w2 & 0xFFFF;
        return read(eaFor(mode, w2)) & 0xFFFF;
    }

    private void addOp(int dst, int op, boolean carry) {
        int a = getReg(dst);
        int c = (carry && (ST & F_C) != 0) ? 1 : 0;
        int res = a + op + c;
        ST &= ~(F_C | F_V | F_Z | F_N);
        if (res > 0xFFFF) ST |= F_C;
        if (((a ^ res) & (op ^ res) & 0x8000) != 0) ST |= F_V;
        res &= 0xFFFF;
        if (res == 0) ST |= F_Z;
        if ((res & 0x8000) != 0) ST |= F_N;
        setReg(dst, res);
    }

    private void subOp(int dst, int op, boolean carry) {
        int a = getReg(dst);
        int c = (carry && (ST & F_C) != 0) ? 1 : 0;
        int res = a - op - c;
        ST &= ~(F_C | F_V | F_Z | F_N);
        if (res < 0) ST |= F_C;
        if (((a ^ op) & (a ^ res) & 0x8000) != 0) ST |= F_V;
        res &= 0xFFFF;
        if (res == 0) ST |= F_Z;
        if ((res & 0x8000) != 0) ST |= F_N;
        setReg(dst, res);
    }

    private void cmpOp(int a, int op) {
        a &= 0xFFFF;
        int res = a - op;
        ST &= ~(F_C | F_V | F_Z | F_N);
        if (res < 0) ST |= F_C;
        if (((a ^ op) & (a ^ res) & 0x8000) != 0) ST |= F_V;
        res &= 0xFFFF;
        if (res == 0) ST |= F_Z;
        if ((res & 0x8000) != 0) ST |= F_N;
    }

    private void mulOp(int dst, int op, boolean signed) {
        int a = getReg(dst);
        long full;
        if (signed) full = (long) sext(a) * (long) sext(op);
        else        full = (long) a * (long) op;
        int res = (int) (full & 0xFFFF);
        ST &= ~(F_N | F_V | F_Z);
        if (signed) { if (full < -32768 || full > 32767) ST |= F_V; }
        else        { if (full > 0xFFFF) ST |= F_V; }
        if (res == 0) ST |= F_Z;
        if ((res & 0x8000) != 0) ST |= F_N;
        setReg(dst, res);
    }

    private void divOp(int dst, int op, int sub) {
        boolean signed = (sub & 1) != 0;
        boolean mod    = (sub & 2) != 0;
        int a = getReg(dst);
        if (op == 0) {
            ST &= ~(F_N | F_Z);
            ST |= F_V;
            if (a == 0) ST |= F_Z;
            if ((a & 0x8000) != 0) ST |= F_N;
            return;
        }
        int res;
        if (signed) {
            int sa = sext(a), so = sext(op);
            res = mod ? (sa % so) : (sa / so);
        } else {
            res = mod ? (a % op) : (a / op);
        }
        res &= 0xFFFF;
        ST &= ~(F_N | F_V | F_Z);
        if (res == 0) ST |= F_Z;
        if ((res & 0x8000) != 0) ST |= F_N;
        setReg(dst, res);
    }

    private void shfOp(int dst, int sub, int cnt) {
        int v = getReg(dst);
        int ejected = 0;
        if (sub == 0) {
            for (int i = 0; i < cnt; i++) { ejected = (v >> 15) & 1; v = (v << 1) & 0xFFFF; }
        } else if (sub == 1) {
            for (int i = 0; i < cnt; i++) { ejected = v & 1; v = (v >>> 1) & 0xFFFF; }
        } else if (sub == 2) {
            int s = sext(v);
            for (int i = 0; i < cnt; i++) { ejected = s & 1; s = s >> 1; }
            v = s & 0xFFFF;
        }
        ST &= ~(F_N | F_Z | F_E);
        if (ejected != 0) ST |= F_E;
        if (v == 0) ST |= F_Z;
        if ((v & 0x8000) != 0) ST |= F_N;
        setReg(dst, v);
    }

    private void rotOp(int dst, int sub, int cnt) {
        int v = getReg(dst);
        int ejected = 0;
        if (sub == 0) {
            for (int i = 0; i < cnt; i++) { ejected = (v >> 15) & 1; v = ((v << 1) | ejected) & 0xFFFF; }
        } else if (sub == 1) {
            for (int i = 0; i < cnt; i++) { ejected = v & 1; v = ((v >>> 1) | (ejected << 15)) & 0xFFFF; }
        } else if (sub == 2) {
            int e = (ST & F_E) != 0 ? 1 : 0;
            ejected = (v >> 15) & 1;
            v = ((v << 1) | e) & 0xFFFF;
        } else {
            int e = (ST & F_E) != 0 ? 1 : 0;
            ejected = v & 1;
            v = ((v >>> 1) | (e << 15)) & 0xFFFF;
        }
        ST &= ~(F_N | F_Z | F_E);
        if (ejected != 0) ST |= F_E;
        if (v == 0) ST |= F_Z;
        if ((v & 0x8000) != 0) ST |= F_N;
        setReg(dst, v);
    }

    private void setNZ(int v) {
        v &= 0xFFFF;
        ST &= ~(F_Z | F_N);
        if (v == 0) ST |= F_Z;
        if ((v & 0x8000) != 0) ST |= F_N;
    }

    private static int sext(int v) {
        v &= 0xFFFF;
        return v >= 0x8000 ? v - 0x10000 : v;
    }

    private void push(int val) {
        SP = (SP - 1) & 0xFFFF;
        write(SP, val & 0xFFFF);
    }

    private int pull() {
        int v = read(SP);
        SP = (SP + 1) & 0xFFFF;
        return v & 0xFFFF;
    }

    private int read(int addr)            { return mem.read(addr & 0xFFFF); }
    private void write(int addr, int val) { mem.write(addr & 0xFFFF, val); }
}

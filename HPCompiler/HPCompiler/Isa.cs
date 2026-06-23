namespace HPCompiler;

enum Layout { None, M, R, I, S, Branch, Swi, Flg, Spt }

sealed class InstrSpec
{
    public byte Op;
    public Layout Layout;
    public int Sub;
    public bool HasReg;
    public bool ImmOk;
    public bool FixedCnt;
    public InstrSpec? RegForm;

    public InstrSpec(byte op, Layout layout, int sub = 0, bool hasReg = false, bool immOk = false, bool fixedCnt = false)
    {
        Op = op;
        Layout = layout;
        Sub = sub;
        HasReg = hasReg;
        ImmOk = immOk;
        FixedCnt = fixedCnt;
    }
}

static class Isa
{
    public static readonly Dictionary<string, InstrSpec> Table = Build();

    public static readonly Dictionary<string, int> Flags = new(StringComparer.OrdinalIgnoreCase)
    {
        { "C", 0 }, { "Z", 1 }, { "I", 2 }, { "N", 3 }, { "V", 4 }, { "E", 5 }
    };

    static Dictionary<string, InstrSpec> Build()
    {
        var t = new Dictionary<string, InstrSpec>(StringComparer.OrdinalIgnoreCase)
        {
            ["NOP"] = new(0x00, Layout.None),
            ["HALT"] = new(0x01, Layout.None),
            ["RETI"] = new(0x03, Layout.None),
            ["RET"] = new(0x52, Layout.None),

            ["FLG"] = new(0x02, Layout.Flg),
            ["SWI"] = new(0x04, Layout.Swi),

            ["LOD"] = new(0x10, Layout.M, 0, true, true),
            ["STO"] = new(0x11, Layout.M, 0, true, false),
            ["LEA"] = new(0x12, Layout.M, 0, true, false),
            ["SWP"] = new(0x13, Layout.R),

            ["ADD"] = new(0x20, Layout.M, 0, true, true),
            ["ADC"] = new(0x20, Layout.M, 1, true, true),
            ["SUB"] = new(0x21, Layout.M, 0, true, true),
            ["SBC"] = new(0x21, Layout.M, 1, true, true),
            ["CMP"] = new(0x22, Layout.M, 0, true, true),
            ["INC"] = new(0x23, Layout.I),
            ["DEC"] = new(0x24, Layout.I),
            ["MULU"] = new(0x25, Layout.M, 0, true, true),
            ["MULS"] = new(0x25, Layout.M, 1, true, true),
            ["MUL"] = new(0x25, Layout.M, 0, true, true),
            ["DIVU"] = new(0x26, Layout.M, 0, true, true),
            ["DIVS"] = new(0x26, Layout.M, 1, true, true),
            ["MODU"] = new(0x26, Layout.M, 2, true, true),
            ["MODS"] = new(0x26, Layout.M, 3, true, true),
            ["DIV"] = new(0x26, Layout.M, 0, true, true),
            ["MOD"] = new(0x26, Layout.M, 2, true, true),
            ["TST"] = new(0x27, Layout.I),

            ["ADDR"] = new(0x28, Layout.R, 0),
            ["ADCR"] = new(0x28, Layout.R, 1),
            ["SUBR"] = new(0x29, Layout.R, 0),
            ["SBCR"] = new(0x29, Layout.R, 1),
            ["CMPR"] = new(0x2A, Layout.R, 0),
            ["MULR"] = new(0x2B, Layout.R, 0),
            ["MLRS"] = new(0x2B, Layout.R, 1),
            ["DIVR"] = new(0x2C, Layout.R, 0),
            ["DVRS"] = new(0x2C, Layout.R, 1),
            ["MODR"] = new(0x2C, Layout.R, 2),
            ["MDRS"] = new(0x2C, Layout.R, 3),

            ["AND"] = new(0x30, Layout.M, 0, true, true),
            ["OR"] = new(0x31, Layout.M, 0, true, true),
            ["EOR"] = new(0x32, Layout.M, 0, true, true),
            ["NOT"] = new(0x33, Layout.I),
            ["NEG"] = new(0x34, Layout.I),
            ["ANDR"] = new(0x35, Layout.R),
            ["ORR"] = new(0x36, Layout.R),
            ["EORR"] = new(0x37, Layout.R),

            ["SHF.LSL"] = new(0x40, Layout.S, 0),
            ["SHF.LSR"] = new(0x40, Layout.S, 1),
            ["SHF.ASR"] = new(0x40, Layout.S, 2),
            ["LSL"] = new(0x40, Layout.S, 0),
            ["LSR"] = new(0x40, Layout.S, 1),
            ["ASR"] = new(0x40, Layout.S, 2),
            ["ROT.ROL"] = new(0x41, Layout.S, 0),
            ["ROT.ROR"] = new(0x41, Layout.S, 1),
            ["ROT.ROLC"] = new(0x41, Layout.S, 2, false, false, true),
            ["ROT.RORC"] = new(0x41, Layout.S, 3, false, false, true),
            ["ROL"] = new(0x41, Layout.S, 0),
            ["ROR"] = new(0x41, Layout.S, 1),
            ["ROLC"] = new(0x41, Layout.S, 2, false, false, true),
            ["RORC"] = new(0x41, Layout.S, 3, false, false, true),

            ["JMP"] = new(0x50, Layout.M, 0, false, false),
            ["CALL"] = new(0x51, Layout.M, 0, false, false),
            ["BCC"] = new(0x53, Layout.Branch),
            ["BCS"] = new(0x54, Layout.Branch),
            ["BEQ"] = new(0x55, Layout.Branch),
            ["BNE"] = new(0x56, Layout.Branch),
            ["BMI"] = new(0x57, Layout.Branch),
            ["BPL"] = new(0x58, Layout.Branch),
            ["BVC"] = new(0x59, Layout.Branch),
            ["BVS"] = new(0x5A, Layout.Branch),
            ["BES"] = new(0x5B, Layout.Branch),
            ["BEC"] = new(0x5C, Layout.Branch),

            ["PSH"] = new(0x60, Layout.I),
            ["PLL"] = new(0x61, Layout.I),
            ["MOV"] = new(0x62, Layout.R),
            ["SPT"] = new(0x63, Layout.Spt),
        };

        Link(t, "ADD", "ADDR");
        Link(t, "ADC", "ADCR");
        Link(t, "SUB", "SUBR");
        Link(t, "SBC", "SBCR");
        Link(t, "CMP", "CMPR");
        Link(t, "MULU", "MULR");
        Link(t, "MULS", "MLRS");
        Link(t, "MUL", "MULR");
        Link(t, "DIVU", "DIVR");
        Link(t, "DIVS", "DVRS");
        Link(t, "MODU", "MODR");
        Link(t, "MODS", "MDRS");
        Link(t, "DIV", "DIVR");
        Link(t, "MOD", "MODR");
        Link(t, "AND", "ANDR");
        Link(t, "OR", "ORR");
        Link(t, "EOR", "EORR");
        return t;
    }

    static void Link(Dictionary<string, InstrSpec> t, string mem, string reg) => t[mem].RegForm = t[reg];
}

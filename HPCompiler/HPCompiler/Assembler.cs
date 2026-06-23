namespace HPCompiler;

enum SKind { Instr, Data, Ascii, Res, IncBin }

sealed class Stmt
{
    public SKind Kind;
    public int Addr;

    public InstrSpec? Spec;
    public string Mnem = "";
    public List<Token[]> Ops = new();
    public Token At;

    public List<Token[]>? Exprs;

    public string Str = "";
    public bool Zero;

    public long Count;
    public long FillVal;

    public ushort[]? Bin;

    public int Bank;
}

sealed class Assembler
{
    readonly Dictionary<string, long> _labels = new();
    readonly Dictionary<string, Token[]> _constExpr = new();
    readonly Dictionary<string, long> _constMemo = new();
    readonly HashSet<string> _resolving = new();
    readonly List<Stmt> _stmts = new();
    readonly Dictionary<int, ushort> _mem = new();

    int _pc;
    int _min = int.MaxValue;
    int _max = -1;
    string _currentGlobal = "";
    int _bank;
    int _mapper;
    int _maxBank;
    int _emitBank;

    public int CurrentAddr;

    const int FIXED_WORDS = 0x4000;
    const int BANK_WORDS = 0x4000;

    public (Dictionary<int, ushort> mem, int min, int max, int mapper, int banks) Assemble(
        List<(string file, int line, List<Token> toks)> lines)
    {
        foreach (var (file, line, toks) in lines) BuildLine(toks);
        foreach (var s in _stmts) Emit(s);
        int banks = _mapper != 0 ? _maxBank + 1 : 0;
        return (_mem, _min, _max, _mapper, banks);
    }

    int FileOffset(int addr)
    {
        if (addr >= FIXED_WORDS && addr < FIXED_WORDS + BANK_WORDS)
            return FIXED_WORDS + _emitBank * BANK_WORDS + (addr - FIXED_WORDS);
        return addr;
    }

    void BuildLine(List<Token> toks)
    {
        int i = 0;
        while (i + 1 < toks.Count && toks[i].Type == TokType.Ident && Sym(toks[i + 1], ":"))
        {
            string lbl = toks[i].Text;
            if (lbl.StartsWith('.')) DefineLabel(_currentGlobal + lbl, _pc);
            else { _currentGlobal = lbl; DefineLabel(lbl, _pc); }
            i += 2;
        }
        if (i >= toks.Count) return;

        Token first = toks[i];
        var tail = new List<Token>(toks.Count - i - 1);
        for (int k = i + 1; k < toks.Count; k++) tail.Add(Qualify(toks[k]));

        if (first.Type == TokType.Ident && tail.Count >= 1 &&
            (Sym(tail[0], "=") || IdentIs(tail[0], "EQU")))
        {
            _constExpr[first.Text] = tail.GetRange(1, tail.Count - 1).ToArray();
            return;
        }

        if (first.Type == TokType.Ident && first.Text.StartsWith('.'))
        {
            Directive(first, tail);
            return;
        }
        Instruction(first, tail);
    }

    Token Qualify(Token t)
    {
        if (t.Type == TokType.Ident && t.Text.Length > 0 && t.Text[0] == '.')
            return new Token(TokType.Ident, _currentGlobal + t.Text, 0, t.File, t.Line);
        return t;
    }

    void Directive(Token name, List<Token> args)
    {
        switch (name.Text.ToLowerInvariant())
        {
            case ".org":
                _pc = (int)(Eval(args.ToArray()) & 0xFFFF);
                break;
            case ".mapper":
                _mapper = (int)(Eval(args.ToArray()) & 0xFF);
                break;
            case ".bank":
            {
                int b = (int)Eval(args.ToArray());
                if (b < 0) throw Err(name, "banco inválido");
                _bank = b;
                if (b > _maxBank) _maxBank = b;
                _pc = FIXED_WORDS;
                break;
            }
            case ".w":
            case ".word":
            case ".dw":
            {
                var ex = SplitCommas(args);
                _stmts.Add(new Stmt { Kind = SKind.Data, Addr = _pc, Bank = _bank, Exprs = ex });
                _pc += ex.Count;
                break;
            }
            case ".ascii":
            case ".asciz":
            case ".string":
            {
                string s = StringArg(args, name);
                bool z = name.Text.ToLowerInvariant() != ".ascii";
                _stmts.Add(new Stmt { Kind = SKind.Ascii, Addr = _pc, Bank = _bank, Str = s, Zero = z });
                _pc += s.Length + (z ? 1 : 0);
                break;
            }
            case ".res":
            case ".ds":
            {
                var parts = SplitCommas(args);
                long count = Eval(parts[0]);
                long fill = parts.Count > 1 ? Eval(parts[1]) : 0;
                _stmts.Add(new Stmt { Kind = SKind.Res, Addr = _pc, Bank = _bank, Count = count, FillVal = fill });
                _pc += (int)count;
                break;
            }
            case ".fill":
            {
                var parts = SplitCommas(args);
                long count = Eval(parts[0]);
                long fill = parts.Count > 1 ? Eval(parts[1]) : 0;
                _stmts.Add(new Stmt { Kind = SKind.Res, Addr = _pc, Bank = _bank, Count = count, FillVal = fill });
                _pc += (int)count;
                break;
            }
            case ".align":
            {
                long a = Eval(args.ToArray());
                if (a <= 0) throw Err(name, "alinhamento inválido");
                int rem = (int)(_pc % a);
                int pad = rem == 0 ? 0 : (int)a - rem;
                if (pad > 0)
                {
                    _stmts.Add(new Stmt { Kind = SKind.Res, Addr = _pc, Bank = _bank, Count = pad, FillVal = 0 });
                    _pc += pad;
                }
                break;
            }
            case ".incbin":
            {
                string rel = StringArg(args, name);
                string path = Path.IsPathRooted(rel) ? rel
                    : Path.Combine(Path.GetDirectoryName(name.File) ?? ".", rel);
                byte[] raw = File.ReadAllBytes(path);
                int wc = (raw.Length + 1) / 2;
                var words = new ushort[wc];
                for (int k = 0; k < wc; k++)
                {
                    int hi = raw[k * 2];
                    int lo = k * 2 + 1 < raw.Length ? raw[k * 2 + 1] : 0;
                    words[k] = (ushort)((hi << 8) | lo);
                }
                _stmts.Add(new Stmt { Kind = SKind.IncBin, Addr = _pc, Bank = _bank, Bin = words });
                _pc += wc;
                break;
            }
            case ".equ":
            case ".set":
            {
                var parts = SplitCommas(args);
                if (parts.Count != 2 || parts[0].Length != 1 || parts[0][0].Type != TokType.Ident)
                    throw Err(name, "uso: .equ NOME, expr");
                _constExpr[parts[0][0].Text] = parts[1];
                break;
            }
            case ".include":
                break;
            default:
                throw Err(name, $"diretiva desconhecida '{name.Text}'");
        }
    }

    void Instruction(Token mnem, List<Token> args)
    {
        if (!Isa.Table.TryGetValue(mnem.Text, out var spec))
            throw Err(mnem, $"instrução desconhecida '{mnem.Text}'");
        var ops = SplitCommas(args);
        if (spec.RegForm != null && ops.Count == 2 && IsReg(ops[1]))
            spec = spec.RegForm;
        int size = spec.Layout == Layout.M ? 2 : 1;
        _stmts.Add(new Stmt { Kind = SKind.Instr, Addr = _pc, Bank = _bank, Spec = spec, Mnem = mnem.Text, Ops = ops, At = mnem });
        _pc += size;
    }

    void Emit(Stmt s)
    {
        CurrentAddr = s.Addr;
        _emitBank = s.Bank;
        switch (s.Kind)
        {
            case SKind.Instr:
            {
                var words = EncodeInstr(s);
                int a = s.Addr;
                foreach (var w in words) Store(a++, w);
                break;
            }
            case SKind.Data:
            {
                int a = s.Addr;
                foreach (var e in s.Exprs!) { CurrentAddr = a; Store(a++, (int)(Eval(e) & 0xFFFF)); }
                break;
            }
            case SKind.Ascii:
            {
                int a = s.Addr;
                foreach (char ch in s.Str) Store(a++, ch & 0xFFFF);
                if (s.Zero) Store(a, 0);
                break;
            }
            case SKind.Res:
            {
                int a = s.Addr;
                for (int k = 0; k < s.Count; k++) Store(a++, (int)(s.FillVal & 0xFFFF));
                break;
            }
            case SKind.IncBin:
            {
                int a = s.Addr;
                bool banked = _mapper != 0 && a >= FIXED_WORDS && a < FIXED_WORDS + BANK_WORDS;
                foreach (var w in s.Bin!)
                {
                    if (banked && a >= FIXED_WORDS + BANK_WORDS)
                    {
                        a = FIXED_WORDS;
                        _emitBank++;
                        if (_emitBank > _maxBank) _maxBank = _emitBank;
                    }
                    Store(a++, w);
                }
                break;
            }
        }
    }

    int[] EncodeInstr(Stmt s)
    {
        var spec = s.Spec!;
        int op = spec.Op;
        switch (spec.Layout)
        {
            case Layout.None:
                return new[] { op << 8 };

            case Layout.M:
            {
                Token[] mem;
                int reg = 0;
                if (spec.HasReg)
                {
                    Need(s, 2);
                    reg = Reg(s, s.Ops[0]);
                    mem = s.Ops[1];
                }
                else
                {
                    Need(s, 1);
                    mem = s.Ops[0];
                }
                var (mode, expr) = ParseAddr(s, mem);
                if (mode == 0 && !spec.ImmOk) throw Err(s.At, "modo imediato ilegal para esta instrução");
                int low = (reg << 5) | (mode << 2) | (spec.Sub & 3);
                int w0 = (op << 8) | low;
                int w1 = (int)(Eval(expr) & 0xFFFF);
                return new[] { w0, w1 };
            }

            case Layout.R:
            {
                Need(s, 2);
                int dst = Reg(s, s.Ops[0]);
                int src = Reg(s, s.Ops[1]);
                int low = (dst << 5) | (src << 2) | (spec.Sub & 3);
                return new[] { (op << 8) | low };
            }

            case Layout.I:
            {
                Need(s, 1);
                int dst = Reg(s, s.Ops[0]);
                int low = (dst << 5) | (spec.Sub & 3);
                return new[] { (op << 8) | low };
            }

            case Layout.S:
            {
                Need(s, 1);
                int dst = Reg(s, s.Ops[0]);
                int cnt = 1;
                if (spec.FixedCnt)
                {
                    if (s.Ops.Count > 1) throw Err(s.At, "esta rotação não aceita contagem");
                }
                else if (s.Ops.Count > 1)
                {
                    var c = s.Ops[1];
                    if (c.Length > 0 && Sym(c[0], "#")) c = c[1..];
                    cnt = (int)Eval(c);
                    if (cnt < 1 || cnt > 8) throw Err(s.At, "contagem de shift deve ser 1..8");
                }
                int field = cnt & 7;
                int low = (dst << 5) | (field << 2) | (spec.Sub & 3);
                return new[] { (op << 8) | low };
            }

            case Layout.Branch:
            {
                Need(s, 1);
                int target = (int)(Eval(s.Ops[0]) & 0xFFFF);
                int off = target - (s.Addr + 1);
                if (off < -128 || off > 127) throw Err(s.At, $"alvo de branch fora de alcance ({off} words)");
                int low = off & 0xFF;
                return new[] { (op << 8) | low };
            }

            case Layout.Swi:
            {
                Need(s, 1);
                var a = s.Ops[0];
                if (a.Length > 0 && Sym(a[0], "#")) a = a[1..];
                int v = (int)(Eval(a) & 0xFF);
                return new[] { (op << 8) | v };
            }

            case Layout.Flg:
            {
                var flat = s.Ops.SelectMany(o => o).ToArray();
                if (flat.Length != 2) throw Err(s.At, "uso: FLG SET|CLR flag");
                Token o = flat[0];
                int bit = IdentIs(o, "SET") ? 1 : IdentIs(o, "CLR") ? 0 : throw Err(s.At, "FLG espera SET ou CLR");
                Token f = flat[1];
                if (!Isa.Flags.TryGetValue(f.Text, out int code)) throw Err(s.At, $"flag inválida '{f.Text}'");
                int low = (bit << 7) | (code << 4);
                return new[] { (op << 8) | low };
            }

            case Layout.Spt:
            {
                if (s.Ops.Count == 1)
                {
                    int dst = Reg(s, s.Ops[0]);
                    return new[] { (op << 8) | (dst << 5) | 1 };
                }
                Need(s, 2);
                if (!IdentIs(s.Ops[0][0], "SP")) throw Err(s.At, "uso: SPT rd | SPT SP, rd");
                int rd = Reg(s, s.Ops[1]);
                return new[] { (op << 8) | (rd << 5) | 2 };
            }
        }
        throw Err(s.At, "layout não suportado");
    }

    (int mode, Token[] expr) ParseAddr(Stmt s, Token[] t)
    {
        if (t.Length == 0) throw Err(s.At, "operando de endereço ausente");
        if (Sym(t[0], "#")) return (0, t[1..]);

        if (Sym(t[0], "("))
        {
            int depth = 0, close = -1;
            for (int k = 0; k < t.Length; k++)
            {
                if (Sym(t[k], "(")) depth++;
                else if (Sym(t[k], ")")) { depth--; if (depth == 0) { close = k; break; } }
            }
            if (close < 0) throw Err(s.At, "')' ausente no operando");
            var inner = t[1..close];
            var post = t[(close + 1)..];
            bool a1 = post.Length == 2 && Sym(post[0], "+") && IdentIs(post[1], "A1");
            bool a0 = inner.Length >= 2 && Sym(inner[^2], "+") && IdentIs(inner[^1], "A0");
            if (a0) inner = inner[..^2];
            int mode = (a0, a1) switch
            {
                (true, true) => 7,
                (true, false) => 5,
                (false, false) => 4,
                _ => throw Err(s.At, "modo de endereçamento inválido")
            };
            return (mode, inner);
        }

        if (IdentIs(t[0], "SP") && t.Length >= 2 && Sym(t[1], "+"))
            return (6, t[2..]);

        if (t.Length >= 2 && Sym(t[^2], "+") && IdentIs(t[^1], "A1"))
            return (3, t[..^2]);
        if (t.Length >= 2 && Sym(t[^2], "+") && IdentIs(t[^1], "A0"))
            return (2, t[..^2]);

        return (1, t);
    }

    int Reg(Stmt s, Token[] op)
    {
        if (op.Length != 1 || !TryReg(op[0], out int code))
            throw Err(s.At, "registrador esperado (D0-D5, A0, A1)");
        return code;
    }

    static bool IsReg(Token[] op) => op.Length == 1 && TryReg(op[0], out _);

    static bool TryReg(Token t, out int code)
    {
        code = 0;
        if (t.Type != TokType.Ident) return false;
        switch (t.Text.ToUpperInvariant())
        {
            case "D0": code = 0; return true;
            case "D1": code = 1; return true;
            case "D2": code = 2; return true;
            case "D3": code = 3; return true;
            case "D4": code = 4; return true;
            case "D5": code = 5; return true;
            case "A0": code = 6; return true;
            case "A1": code = 7; return true;
            default: return false;
        }
    }

    void DefineLabel(string name, int value)
    {
        if (_labels.ContainsKey(name) || _constExpr.ContainsKey(name))
            throw new AsmException($"símbolo redefinido: '{name}'");
        _labels[name] = value;
    }

    public long ResolveSymbol(string name, Token ctx)
    {
        if (_labels.TryGetValue(name, out long lv)) return lv;
        if (_constMemo.TryGetValue(name, out long mv)) return mv;
        if (_constExpr.TryGetValue(name, out var toks))
        {
            if (!_resolving.Add(name)) throw new AsmException($"{ctx.File}:{ctx.Line}: referência cíclica em '{name}'");
            long v = Eval(toks);
            _resolving.Remove(name);
            _constMemo[name] = v;
            return v;
        }
        throw new AsmException($"{ctx.File}:{ctx.Line}: símbolo indefinido '{name}'");
    }

    long Eval(Token[] toks) => new ExprParser(toks, this).Parse();

    void Store(int addr, int word)
    {
        int off = FileOffset(addr & 0xFFFF);
        _mem[off] = (ushort)(word & 0xFFFF);
        if (off < _min) _min = off;
        if (off > _max) _max = off;
    }

    static List<Token[]> SplitCommas(IReadOnlyList<Token> toks)
    {
        var res = new List<Token[]>();
        var cur = new List<Token>();
        int depth = 0;
        foreach (var tk in toks)
        {
            if (Sym(tk, "(")) depth++;
            else if (Sym(tk, ")")) depth--;
            if (depth == 0 && Sym(tk, ",")) { res.Add(cur.ToArray()); cur = new(); }
            else cur.Add(tk);
        }
        if (cur.Count > 0) res.Add(cur.ToArray());
        return res;
    }

    static string StringArg(List<Token> args, Token name)
    {
        if (args.Count != 1 || args[0].Type != TokType.String)
            throw Err(name, $"{name.Text} espera uma string");
        return args[0].Text;
    }

    void Need(Stmt s, int n)
    {
        if (s.Ops.Count < n) throw Err(s.At, $"'{s.Mnem}' espera {n} operando(s)");
    }

    static bool Sym(Token t, string s) => t.Type == TokType.Symbol && t.Text == s;
    static bool IdentIs(Token t, string s) => t.Type == TokType.Ident && string.Equals(t.Text, s, StringComparison.OrdinalIgnoreCase);

    static AsmException Err(Token t, string m) => new($"{t.File}:{t.Line}: {m}");
}

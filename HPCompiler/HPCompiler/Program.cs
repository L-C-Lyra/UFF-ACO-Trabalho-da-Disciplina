using HPCompiler;

return Cli.Run(args);

static class Cli
{
    public static int Run(string[] args)
    {
        if (args.Length > 0 && args[0] == "vgm")
            return Vgm.Run(args[1..]);

        string? input = null;
        string? output = null;
        bool header = true;

        for (int i = 0; i < args.Length; i++)
        {
            string a = args[i];
            string key = a;
            string? inline = null;
            int sep = a.IndexOfAny(new[] { ':', '=' });
            if (a.StartsWith('-') && sep > 0) { key = a[..sep]; inline = a[(sep + 1)..]; }

            switch (key)
            {
                case "-o":
                case "--out":
                    if (++i >= args.Length) return Fail("falta caminho após " + a);
                    output = args[i];
                    break;
                case "--header":
                    if (inline != null) { if (!ParseBool(inline, out header)) return Fail("valor inválido para --header: " + inline); }
                    else if (i + 1 < args.Length && ParseBool(args[i + 1], out header)) i++;
                    else header = true;
                    break;
                case "--no-header":
                case "--raw":
                    header = false;
                    break;
                case "-h":
                case "--help":
                    Usage();
                    return 0;
                default:
                    if (a.StartsWith('-')) return Fail("opção desconhecida: " + a);
                    input = a;
                    break;
            }
        }

        if (input == null) { Usage(); return 1; }
        output ??= Path.ChangeExtension(input, ".hrom");

        try
        {
            var lines = Source.Load(input);
            var asm = new Assembler();
            var (mem, _, max, mapper, banks) = asm.Assemble(lines);
            byte[] image = BuildImage(mem, max, header, mapper, banks);
            File.WriteAllBytes(output, image);
            int words = max >= 0 ? max + 1 : 0;
            string bk = mapper != 0 ? $"  mapper={mapper} banks={banks}" : "";
            Console.WriteLine($"OK: {output}  {words} words ({image.Length} bytes){(header ? "  +header HROM" : "")}{bk}");
            return 0;
        }
        catch (AsmException e)
        {
            Console.Error.WriteLine("erro: " + e.Message);
            return 1;
        }
        catch (FileNotFoundException e)
        {
            Console.Error.WriteLine("erro: arquivo não encontrado: " + e.FileName);
            return 1;
        }
    }

    static byte[] BuildImage(Dictionary<int, ushort> mem, int max, bool header, int mapper, int banks)
    {
        int words = max >= 0 ? max + 1 : 0;
        var payload = new byte[words * 2];
        for (int k = 0; k < words; k++)
        {
            ushort w = mem.TryGetValue(k, out var v) ? v : (ushort)0;
            payload[k * 2] = (byte)(w >> 8);
            payload[k * 2 + 1] = (byte)(w & 0xFF);
        }
        if (!header) return payload;

        var head = new byte[16];
        head[0] = (byte)'H';
        head[1] = (byte)'R';
        head[2] = (byte)'O';
        head[3] = (byte)'M';
        head[4] = 0x01;
        head[5] = (byte)mapper;
        head[6] = (byte)(banks >> 8); head[7] = (byte)(banks & 0xFF);
        var image = new byte[head.Length + payload.Length];
        Array.Copy(head, image, head.Length);
        Array.Copy(payload, 0, image, head.Length, payload.Length);
        return image;
    }

    static bool ParseBool(string s, out bool value)
    {
        switch (s.ToLowerInvariant())
        {
            case "true": case "1": case "yes": case "on": value = true; return true;
            case "false": case "0": case "no": case "off": value = false; return true;
            default: value = true; return false;
        }
    }

    static int Fail(string m)
    {
        Console.Error.WriteLine("erro: " + m);
        return 1;
    }

    static void Usage()
    {
        Console.WriteLine("HPCompiler - assembler HASM para HP16 / HVS-16");
        Console.WriteLine("uso: hpcasm <entrada.hasm> [-o saida.hrom] [--header true|false]");
        Console.WriteLine("  -o, --out       arquivo de saida (padrao: <entrada>.hrom)");
        Console.WriteLine("  --header [bool]  inclui cabecalho HROM de 16 bytes (padrao: true)");
        Console.WriteLine("  --no-header      atalho para --header false (imagem binaria pura)");
    }
}

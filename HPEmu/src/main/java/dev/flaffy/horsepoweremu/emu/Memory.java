package dev.flaffy.horsepoweremu.emu;

import dev.flaffy.horsepoweremu.emu.audio.AudioChip;
import dev.flaffy.horsepoweremu.emu.video.V9958;

public class Memory {

    public static final int SIZE = 65536;
    public static final int ROM_BASE  = 0x0000;
    public static final int ROM_END = 0x7FFF;
    public static final int RAM_BASE = 0x8000;
    public static final int RAM_END = 0xFEFF;
    public static final int MMIO_BASE = 0xFF00;

    private final int[]  ram = new int[RAM_END - RAM_BASE + 1];
    private final long[] writeTimes = new long[SIZE];

    private ROM rom;
    private V9958 vdp;
    private AudioChip apu;
    private SystemIO  io;

    public void setROM(ROM r) { this.rom = r; }
    public void setVDP(V9958 v) { this.vdp = v; }
    public void setAPU(AudioChip a)  { this.apu = a; }
    public void setIO(SystemIO s) { this.io = s; }

    public int read(int addr) {
        addr &= 0xFFFF;
        if (addr <= ROM_END)  return rom != null ? rom.read(addr) : 0;
        if (addr <= RAM_END)  return ram[addr - RAM_BASE];
        return readMMIO(addr);
    }

    public void write(int addr, int value) {
        addr &= 0xFFFF;
        value &= 0xFFFF;
        if (addr <= ROM_END) {
            return;
        } else if (addr <= RAM_END) {
            ram[addr - RAM_BASE] = value;
            writeTimes[addr] = System.currentTimeMillis();
        } else {
            writeMMIO(addr, value);
            writeTimes[addr] = System.currentTimeMillis();
        }
    }

    private int readMMIO(int addr) {
        switch (addr) {
            case 0xFF00: return vdp != null ? vdp.readData() : 0;
            case 0xFF01: return vdp != null ? vdp.readStatus() : 0;
            case 0xFF09: return apu != null ? apu.readStatus() : 0;
            case 0xFF0B: return apu != null ? apu.readData() : 0;
            default:
                if (addr >= 0xFF10 && addr <= 0xFF1B) return io != null ? io.read(addr) : 0;
                return 0;
        }
    }

    private void writeMMIO(int addr, int value) {
        int b = value & 0xFF;
        switch (addr) {
            case 0xFF00: if (vdp != null) vdp.writeData(b); break;
            case 0xFF01: if (vdp != null) vdp.writeCtrl(b); break;
            case 0xFF02: if (vdp != null) vdp.writePalette(b); break;
            case 0xFF03: if (vdp != null) vdp.writeRegIndirect(b); break;
            case 0xFF08: if (apu != null) apu.writeAddr0(b); break;
            case 0xFF09: if (apu != null) apu.writeData0(b); break;
            case 0xFF0A: if (apu != null) apu.writeAddr1(b); break;
            case 0xFF0B: if (apu != null) apu.writeData1(b); break;
            default:
                if (addr >= 0xFF10 && addr <= 0xFF1B && io != null) io.write(addr, b);
        }
    }

    public long getWriteTime(int addr) { return writeTimes[addr & 0xFFFF]; }

    public boolean isROM(int addr)  { return addr >= ROM_BASE && addr <= ROM_END; }
    public boolean isMMIO(int addr) { return addr >= MMIO_BASE; }

    public void clearRAM() {
        java.util.Arrays.fill(ram, 0);
        java.util.Arrays.fill(writeTimes, 0);
    }

    public void fullClear() { clearRAM(); }
}

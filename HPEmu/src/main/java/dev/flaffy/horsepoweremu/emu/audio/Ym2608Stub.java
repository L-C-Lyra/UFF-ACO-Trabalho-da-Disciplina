package dev.flaffy.horsepoweremu.emu.audio;

/**
 * Silent stand-in for the YM2608. Stores register writes so the bus behaves
 * sanely but produces no audio and never raises an IRQ. Used by headless tools
 * and as a fallback when the native ymfm library cannot be loaded.
 */
public class Ym2608Stub implements AudioChip {

    private final int[] reg = new int[0x200];
    private int addr0, addr1;

    @Override public void reset() {
        java.util.Arrays.fill(reg, 0);
        addr0 = addr1 = 0;
    }

    @Override public void writeAddr0(int val) { addr0 = val & 0xff; }
    @Override public void writeData0(int val) { reg[addr0 & 0xff] = val & 0xff; }
    @Override public void writeAddr1(int val) { addr1 = val & 0xff; }
    @Override public void writeData1(int val) { reg[0x100 | (addr1 & 0xff)] = val & 0xff; }

    @Override public int readStatus() { return 0; }
    @Override public int readData()   { return reg[0x100 | (addr1 & 0xff)] & 0xff; }

    @Override public boolean isIRQ() { return false; }

    @Override public int sampleRate() { return 0; }

    @Override public int generate(short[] out, int frames) {
        int n = Math.min(frames * 2, out.length);
        java.util.Arrays.fill(out, 0, n, (short) 0);
        return frames;
    }
}

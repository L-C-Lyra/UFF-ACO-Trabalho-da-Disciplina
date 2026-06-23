package dev.flaffy.horsepoweremu.emu;

public class SystemIO {

    public static final int IRQ_VDP   = 0x01;
    public static final int IRQ_SOUND = 0x02;
    public static final int IRQ_INPUT = 0x04;

    private int irqEnable = 0;
    private int irqStatus = 0;
    private int inputCtrl = 0;
    private int sysctl = 0;
    private int romBank = 0;

    private int joy1 = 0, joy2 = 0;

    private final int[] fifo = new int[256];
    private final int[] fifoDev = new int[256];
    private final int[] fifoMake = new int[256];
    private int fifoHead = 0, fifoTail = 0, fifoCount = 0;
    private boolean overflow = false;

    public synchronized void reset() {
        irqEnable = 0; irqStatus = 0; inputCtrl = 0; sysctl = 0; romBank = 0;
        joy1 = joy2 = 0;
        fifoHead = fifoTail = fifoCount = 0;
        overflow = false;
    }

    public synchronized void setVDPIRQ(boolean active) { setSource(IRQ_VDP, active); }
    public synchronized void setSoundIRQ(boolean active) { setSource(IRQ_SOUND, active); }

    private void setSource(int bit, boolean active) {
        if (active) irqStatus |= bit; else irqStatus &= ~bit;
    }

    public synchronized boolean irqActive() { return (irqStatus & irqEnable) != 0; }

    public int getRomBank() { return romBank; }

    public synchronized void pushInputEvent(int dev, boolean make, int payload) {
        if ((inputCtrl & 0x01) == 0) return;
        if (fifoCount >= fifo.length) { overflow = true; return; }
        fifo[fifoTail] = payload & 0xff;
        fifoDev[fifoTail] = dev & 0x07;
        fifoMake[fifoTail] = make ? 1 : 0;
        fifoTail = (fifoTail + 1) & 0xff;
        fifoCount++;
        irqStatus |= IRQ_INPUT;
    }

    public synchronized void setJoy1(int v) { joy1 = v & 0xff; }
    public synchronized void setJoy2(int v) { joy2 = v & 0xff; }

    public synchronized int read(int addr) {
        switch (addr) {
            case 0xFF10: {
                int v = 0;
                if (overflow) v |= 0x80;
                if (fifoCount > 0) {
                    v |= 0x01;
                    v |= (fifoMake[fifoHead] != 0) ? 0x10 : 0x00;
                    v |= (fifoDev[fifoHead] & 0x07) << 1;
                }
                return v;
            }
            case 0xFF11: {
                if (fifoCount == 0) return 0;
                int payload = fifo[fifoHead];
                fifoHead = (fifoHead + 1) & 0xff;
                fifoCount--;
                if (fifoCount == 0) irqStatus &= ~IRQ_INPUT;
                return payload;
            }
            case 0xFF13: return joy1;
            case 0xFF14: return joy2;
            case 0xFF18: return irqEnable;
            case 0xFF19: return irqStatus;
            case 0xFF1A: return romBank;
            case 0xFF1B: return sysctl;
            default: return 0;
        }
    }

    public synchronized void write(int addr, int val) {
        val &= 0xff;
        switch (addr) {
            case 0xFF12:
                inputCtrl = val;
                if ((val & 0x02) != 0) { fifoHead = fifoTail = fifoCount = 0; overflow = false; irqStatus &= ~IRQ_INPUT; }
                break;
            case 0xFF18: irqEnable = val; break;
            case 0xFF19: irqStatus &= ~val; break;
            case 0xFF1A: romBank = val; break;
            case 0xFF1B: sysctl = val; break;
        }
    }
}

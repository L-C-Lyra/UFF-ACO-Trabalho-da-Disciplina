package dev.flaffy.horsepoweremu.emu;

import dev.flaffy.horsepoweremu.emu.audio.AudioChip;
import dev.flaffy.horsepoweremu.emu.audio.AudioOutput;
import dev.flaffy.horsepoweremu.emu.audio.Ym2608Native;
import dev.flaffy.horsepoweremu.emu.audio.Ym2608Stub;
import dev.flaffy.horsepoweremu.emu.video.V9958;

import java.io.File;
import java.io.IOException;

public class Emulator {

    public static final double CLOCK_HZ = 3_579_545.0;
    public static final int FPS = 60;
    public static final int LINES_PER_FRAME = 262;
    public static final double CYCLES_PER_LINE = CLOCK_HZ / FPS / LINES_PER_FRAME;
    public static final long FRAME_NANOS = 1_000_000_000L / FPS;

    public final Memory memory = new Memory();
    public final CPU  cpu  = new CPU();
    public final ROM rom = new ROM();
    public final V9958 vdp = new V9958();
    public final AudioChip apu = createAudio();
    public final SystemIO io = new SystemIO();

    private final AudioOutput audio = new AudioOutput();
    private short[] audioBuf;
    private short[] lineScratch;
    private int audioFrames;
    private double sampleAccum;
    private double samplesPerLine;

    private volatile boolean alive = false;
    private volatile boolean paused = true;
    private volatile boolean stepReq = false;
    private volatile boolean fastMode = false;
    private volatile double  speed = 1.0;   // 1.0 = full speed; < 1.0 slows down

    private Thread thread;
    private final Runnable onFrame;

    public Emulator(Runnable onFrame) {
        this.onFrame = onFrame;
        rom.setIO(io);
        memory.setROM(rom);
        memory.setVDP(vdp);
        memory.setAPU(apu);
        memory.setIO(io);
        cpu.setMemory(memory);

        int rate = apu.sampleRate();
        if (rate > 0) {
            samplesPerLine = (double) rate / (FPS * LINES_PER_FRAME);
            audioBuf = new short[((rate / FPS) + 64) * 2];
            lineScratch = new short[((int) Math.ceil(samplesPerLine) + 4) * 2];
            audio.open(rate);
        } else {
            audioBuf = new short[0];
            lineScratch = new short[0];
        }

        resetAll();
    }

    private static AudioChip createAudio() {
        try {
            return new Ym2608Native();
        } catch (Throwable t) {
            System.err.println("[audio] ymfm native nao esta disponivel" + t);
            return new Ym2608Stub();
        }
    }

    private void resetAll() {
        io.reset();
        apu.reset();
        vdp.reset();
        cpu.reset();
        for (int i = 0; i < LINES_PER_FRAME; i++) vdp.clockLine();
    }

    public void start() {
        alive  = true;
        thread = new Thread(this::loop, "emu-cpu");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        alive = false;
        if (thread != null) thread.interrupt();
        audio.close();
        apu.close();
    }

    public boolean isAudioMuted() { return audio.isMuted(); }
    public void setAudioMuted(boolean m) { audio.setMuted(m); }
    public int getAudioSampleRate() { return apu.sampleRate(); }

    private void clockLine() {
        vdp.clockLine();
        pumpAudioLine();
        io.setVDPIRQ(vdp.isIRQ());
        io.setSoundIRQ(apu.isIRQ());
        cpu.irqLine = io.irqActive();
    }

    private void pumpAudioLine() {
        if (samplesPerLine <= 0) return;
        sampleAccum += samplesPerLine;
        int n = (int) sampleAccum;
        if (n <= 0) return;
        sampleAccum -= n;
        int cap = Math.min(audioBuf.length / 2 - audioFrames, lineScratch.length / 2);
        if (n > cap) n = cap;
        if (n <= 0) return;
        apu.generate(lineScratch, n);
        System.arraycopy(lineScratch, 0, audioBuf, audioFrames * 2, n * 2);
        audioFrames += n;
    }

    private void drainAudio(boolean play) {
        if (play && audioFrames > 0) audio.write(audioBuf, audioFrames);
        audioFrames = 0;
    }

    private void runLine() {
        long start = cpu.totalCycles;
        while ((cpu.totalCycles - start) < CYCLES_PER_LINE) {
            if (cpu.halted && !cpu.irqLine && !cpu.nmiPending) break;
            cpu.step();
        }
        clockLine();
    }

    private void loop() {
        long nextFrame = System.nanoTime();

        while (alive) {
            if (paused) {
                if (stepReq) {
                    stepReq = false;
                    if (!cpu.halted || cpu.irqLine || cpu.nmiPending) cpu.step();
                    clockLine();
                    drainAudio(false);
                    onFrame.run();
                }
                sleep(5);
                nextFrame = System.nanoTime();
                continue;
            }

            for (int ln = 0; ln < LINES_PER_FRAME && !paused; ln++) runLine();

            drainAudio(!fastMode);

            onFrame.run();

            if (fastMode) {
                nextFrame = System.nanoTime();
            } else {
                nextFrame += (long) (FRAME_NANOS / speed);
                long now = System.nanoTime();
                long diff = nextFrame - now;
                if (diff > 0) sleep(diff / 1_000_000L, (int) (diff % 1_000_000L));
                else if (diff < -5 * FRAME_NANOS) nextFrame = now;
            }
        }
    }

    public void loadROM(File f) throws IOException {
        boolean was = paused;
        paused = true;
        sleep(20);
        memory.fullClear();
        rom.load(f);
        resetAll();
        paused = was;
    }

    public void reset() {
        boolean was = paused;
        paused = true;
        sleep(10);
        memory.clearRAM();
        resetAll();
        paused = was;
    }
//sda
    public void setPaused(boolean p) { paused = p; }
    public boolean isPaused() { return paused; }
    public void requestStep() { stepReq = true; }
    public void setFastMode(boolean v) { fastMode = v; }
    public boolean isFastMode() { return fastMode; }

    public void setSpeed(double factor) { speed = Math.max(0.05, Math.min(4.0, factor)); }
    public double getSpeed() { return speed; }

    public double getClockMhz() { return CLOCK_HZ / 1_000_000.0; }

    private static void sleep(long ms) { sleep(ms, 0); }

    private static void sleep(long ms, int ns) {
        if (ms <= 0 && ns <= 0) return;
        try { Thread.sleep(Math.max(0, ms), Math.max(0, Math.min(999_999, ns))); }
        catch (InterruptedException ignored) {}
    }
}

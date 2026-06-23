package dev.flaffy.horsepoweremu.emu.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;


public final class AudioOutput {

    private SourceDataLine line;
    private byte[] bytes = new byte[0];
    private boolean muted = false;

    public boolean open(int sampleRate) {
        if (sampleRate <= 0) return false;
        try {
            AudioFormat fmt = new AudioFormat(sampleRate, 16, 2, true, false); // signed, little-endian
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) return false;
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(fmt, sampleRate / 5 * 2 * 2); // ~250ms
            line.start();
            return true;
        } catch (Exception e) {
            line = null;
            return false;
        }
    }

    public void write(short[] samples, int frames) {
        if (line == null || muted) return;
        int shorts = frames * 2;
        int needed = shorts * 2;
        if (bytes.length < needed) bytes = new byte[needed];
        for (int i = 0; i < shorts; i++) {
            short s = samples[i];
            bytes[2 * i]     = (byte) (s & 0xff);
            bytes[2 * i + 1] = (byte) ((s >> 8) & 0xff);
        }
        int free = line.available();
        int toWrite = Math.min(needed, free);
        if (toWrite > 0) line.write(bytes, 0, toWrite);
    }

    public void setMuted(boolean m) {
        muted = m;
        if (line != null && m) line.flush();
    }

    public boolean isMuted() { return muted; }

    public void close() {
        if (line != null) {
            line.drain();
            line.stop();
            line.close();
            line = null;
        }
    }
}

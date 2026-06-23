// hpym2608.cpp — C ABI wrapper around ymfm::ym2608 (OPNA) for the HorsePower HVS-16.
//
// Exposes a flat C interface that the Java side binds via the Foreign Function &
// Memory API. One opaque handle per chip instance. The wrapper owns:
//   - timer scheduling (Timer A/B -> engine_timer_expired), driving the IRQ line
//   - the ADPCM-B external RAM (32 KB on the real board; we allocate the chip max)
//   - the optional ADPCM-A rhythm ROM (silent if not loaded)
//
// Output: ym2608 produces 3 channels (FM L, FM R, SSG mono). We fold SSG into
// both sides and clamp to int16 interleaved stereo.

#include "ymfm/ymfm_opn.h"

#include <cstdint>
#include <cstring>

#define HPYM_EXPORT extern "C" __attribute__((visibility("default")))

using namespace ymfm;

namespace {

class hp_ym2608 : public ymfm_interface
{
public:
    hp_ym2608(uint32_t clock, uint32_t out_rate)
        : m_chip(*this), m_clock(clock)
    {
        // Lowest fidelity keeps ymfm's internal common rate (and CPU cost) down;
        // we resample to the host output rate ourselves anyway.
        m_chip.set_fidelity(OPN_FIDELITY_MIN);
        m_native_rate = m_chip.sample_rate(clock);
        m_out_rate = (out_rate > 0) ? out_rate : m_native_rate;
        m_clocks_per_sample = (m_native_rate > 0)
            ? double(clock) / double(m_native_rate)
            : 48.0;
        m_ratio = (m_out_rate > 0) ? double(m_native_rate) / double(m_out_rate) : 1.0;
        m_resamp_accum = 0.0;
        m_last_l = m_last_r = 0;
        m_timer[0] = m_timer[1] = -1.0;
        m_irq = false;
        std::memset(m_adpcmb, 0, sizeof(m_adpcmb));
        m_adpcma = nullptr;
        m_adpcma_size = 0;
        m_chip.reset();
    }

    ~hp_ym2608() override { delete[] m_adpcma; }

    uint32_t sample_rate() const { return m_out_rate; }

    void reset()
    {
        m_chip.reset();
        m_resamp_accum = 0.0;
        m_last_l = m_last_r = 0;
        m_timer[0] = m_timer[1] = -1.0;
        m_irq = false;
    }

    // off: 0=addr bank0, 1=data bank0, 2=addr bank1, 3=data bank1
    void write(uint32_t off, uint32_t data) { m_chip.write(off & 3, uint8_t(data)); }
    uint32_t read(uint32_t off)             { return m_chip.read(off & 3); }
    bool irq() const                        { return m_irq; }

    // Generate `frames` interleaved stereo int16 samples at the output rate,
    // box-averaging native samples per output frame (cheap low-pass on decimation).
    void generate(int16_t *out, uint32_t frames)
    {
        ym2608::output_data o;
        for (uint32_t i = 0; i < frames; i++)
        {
            m_resamp_accum += m_ratio;
            int steps = int(m_resamp_accum);
            m_resamp_accum -= steps;

            if (steps > 0)
            {
                int64_t sum_l = 0, sum_r = 0;
                for (int k = 0; k < steps; k++)
                {
                    m_chip.generate(&o);
                    advance_timers();
                    sum_l += o.data[0] + o.data[2];
                    sum_r += o.data[1] + o.data[2];
                }
                m_last_l = int32_t(sum_l / steps);
                m_last_r = int32_t(sum_r / steps);
            }
            out[2 * i + 0] = clamp16(m_last_l);
            out[2 * i + 1] = clamp16(m_last_r);
        }
    }

    void set_adpcma_rom(const uint8_t *data, uint32_t size)
    {
        delete[] m_adpcma;
        m_adpcma = nullptr;
        m_adpcma_size = 0;
        if (data != nullptr && size > 0)
        {
            m_adpcma = new uint8_t[size];
            std::memcpy(m_adpcma, data, size);
            m_adpcma_size = size;
        }
    }

    // ----- ymfm_interface -----
    void ymfm_set_timer(uint32_t tnum, int32_t duration_in_clocks) override
    {
        if (tnum < 2)
            m_timer[tnum] = (duration_in_clocks < 0) ? -1.0 : double(duration_in_clocks);
    }

    void ymfm_update_irq(bool asserted) override { m_irq = asserted; }

    uint8_t ymfm_external_read(access_class type, uint32_t address) override
    {
        if (type == ACCESS_ADPCM_B)
            return (address < sizeof(m_adpcmb)) ? m_adpcmb[address] : 0;
        if (type == ACCESS_ADPCM_A)
            return (m_adpcma && address < m_adpcma_size) ? m_adpcma[address] : 0;
        return 0;
    }

    void ymfm_external_write(access_class type, uint32_t address, uint8_t data) override
    {
        if (type == ACCESS_ADPCM_B && address < sizeof(m_adpcmb))
            m_adpcmb[address] = data;
    }

private:
    static int16_t clamp16(int32_t v)
    {
        if (v < -32768) return -32768;
        if (v >  32767) return  32767;
        return int16_t(v);
    }

    void advance_timers()
    {
        for (int t = 0; t < 2; t++)
        {
            if (m_timer[t] >= 0.0)
            {
                m_timer[t] -= m_clocks_per_sample;
                if (m_timer[t] < 0.0)
                {
                    m_timer[t] = -1.0;                  // engine reloads via ymfm_set_timer
                    m_engine->engine_timer_expired(t);
                }
            }
        }
    }

    ym2608   m_chip;
    uint32_t m_clock;
    uint32_t m_native_rate;        // ymfm internal common rate (clock/48 at MIN)
    uint32_t m_out_rate;           // host output rate
    double   m_clocks_per_sample;  // master clocks per native sample
    double   m_ratio;              // native samples per output sample
    double   m_resamp_accum;
    int32_t  m_last_l, m_last_r;
    double   m_timer[2];
    bool     m_irq;

    uint8_t  m_adpcmb[0x40000];   // 256 KB: chip maximum (board populates 32 KB)
    uint8_t *m_adpcma;            // optional rhythm ROM
    uint32_t m_adpcma_size;
};

} // namespace

HPYM_EXPORT void *   hpym_create(uint32_t clock, uint32_t out_rate) { return new hp_ym2608(clock, out_rate); }
HPYM_EXPORT void     hpym_destroy(void *p)                       { delete static_cast<hp_ym2608 *>(p); }
HPYM_EXPORT void     hpym_reset(void *p)                         { static_cast<hp_ym2608 *>(p)->reset(); }
HPYM_EXPORT uint32_t hpym_sample_rate(void *p)                   { return static_cast<hp_ym2608 *>(p)->sample_rate(); }
HPYM_EXPORT void     hpym_write(void *p, uint32_t off, uint32_t data) { static_cast<hp_ym2608 *>(p)->write(off, data); }
HPYM_EXPORT uint32_t hpym_read(void *p, uint32_t off)            { return static_cast<hp_ym2608 *>(p)->read(off); }
HPYM_EXPORT int      hpym_irq(void *p)                           { return static_cast<hp_ym2608 *>(p)->irq() ? 1 : 0; }
HPYM_EXPORT void     hpym_generate(void *p, int16_t *out, uint32_t frames) { static_cast<hp_ym2608 *>(p)->generate(out, frames); }
HPYM_EXPORT void     hpym_set_adpcma_rom(void *p, const uint8_t *data, uint32_t size) { static_cast<hp_ym2608 *>(p)->set_adpcma_rom(data, size); }

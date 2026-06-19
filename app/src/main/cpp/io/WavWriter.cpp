#include "io/WavWriter.h"
#include <cstdio>
#include <cstdint>
#include <algorithm>
namespace {
bool w32(FILE* f, uint32_t v){ unsigned char b[4]={(unsigned char)v,(unsigned char)(v>>8),(unsigned char)(v>>16),(unsigned char)(v>>24)}; return fwrite(b,1,4,f)==4; }
bool w16(FILE* f, uint16_t v){ unsigned char b[2]={(unsigned char)v,(unsigned char)(v>>8)}; return fwrite(b,1,2,f)==2; }
}
namespace io {
bool writeWav(const std::string& path, const float* s, size_t n, int sr) {
    if (n > UINT32_MAX / 2) return false;             // dataBytes would overflow uint32
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) return false;
    uint32_t dataBytes = (uint32_t)(n * 2);
    bool ok = true;
    ok = ok && fwrite("RIFF",1,4,f)==4;
    ok = ok && w32(f, 36 + dataBytes);
    ok = ok && fwrite("WAVE",1,4,f)==4;
    ok = ok && fwrite("fmt ",1,4,f)==4;
    ok = ok && w32(f,16) && w16(f,1) && w16(f,1);
    ok = ok && w32(f,(uint32_t)sr) && w32(f,(uint32_t)(sr*2)) && w16(f,2) && w16(f,16);
    ok = ok && fwrite("data",1,4,f)==4;
    ok = ok && w32(f, dataBytes);
    for (size_t i = 0; i < n && ok; ++i) {
        float v = std::max(-1.0f, std::min(1.0f, s[i]));
        ok = ok && w16(f, (uint16_t)(int16_t)(v * 32767.0f));
    }
    if (fclose(f) != 0) ok = false;
    if (!ok) remove(path.c_str());   // never leave a corrupt evidence file
    return ok;
}
}

#include "io/WavWriter.h"
#include <cstdio>
#include <cstdint>
#include <algorithm>
namespace {
void w32(FILE* f, uint32_t v){ unsigned char b[4]={(unsigned char)v,(unsigned char)(v>>8),(unsigned char)(v>>16),(unsigned char)(v>>24)}; fwrite(b,1,4,f);}
void w16(FILE* f, uint16_t v){ unsigned char b[2]={(unsigned char)v,(unsigned char)(v>>8)}; fwrite(b,1,2,f);}
}
namespace io {
bool writeWav(const std::string& path, const float* s, size_t n, int sr) {
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) return false;
    uint32_t dataBytes = (uint32_t)(n * 2);
    fwrite("RIFF",1,4,f); w32(f, 36 + dataBytes); fwrite("WAVE",1,4,f);
    fwrite("fmt ",1,4,f); w32(f,16); w16(f,1); w16(f,1);
    w32(f, (uint32_t)sr); w32(f, (uint32_t)(sr*2)); w16(f,2); w16(f,16);
    fwrite("data",1,4,f); w32(f, dataBytes);
    for (size_t i = 0; i < n; ++i) {
        float v = std::max(-1.0f, std::min(1.0f, s[i]));
        w16(f, (uint16_t)(int16_t)(v * 32767.0f));
    }
    fclose(f);
    return true;
}
}

#pragma once
#include <cstddef>
#include <array>
namespace dsp {
class AWeighting {
public:
    explicit AWeighting(int sampleRate);
    void process(float* samples, size_t n); // in-place
    void reset();
private:
    struct Biquad { double b0,b1,b2,a1,a2; double z1=0,z2=0;
        double step(double x){ double y=b0*x+z1; z1=b1*x-a1*y+z2; z2=b2*x-a2*y; return y; } };
    std::array<Biquad,3> stages_;
};
}

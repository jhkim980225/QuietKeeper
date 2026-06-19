#pragma once
#include <vector>
#include <cstddef>
namespace buffer {
class RingBuffer {
public:
    explicit RingBuffer(size_t capacity);
    void write(const float* data, size_t n);
    std::vector<float> lastN(size_t n) const;            // most recent n (clamped to filled)
    std::vector<float> extract(size_t pre, size_t post) const; // most recent (pre+post)
    size_t capacity() const { return cap_; }
    size_t filled() const { return filled_; }
    void reset() noexcept { head_ = 0; filled_ = 0; }
    // Non-allocating: copies the most recent (pre+post) samples (clamped to filled) into out
    // (chronological, oldest-first). Caller guarantees out has room for (pre+post) floats.
    // Returns the number of samples written.
    size_t extractInto(size_t pre, size_t post, float* out) const;
private:
    std::vector<float> buf_;
    size_t cap_;
    size_t head_ = 0;   // next write position
    size_t filled_ = 0; // number filled (<= cap_)
};
}

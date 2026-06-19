#include "buffer/RingBuffer.h"
#include <algorithm>
#include <cassert>
namespace buffer {
RingBuffer::RingBuffer(size_t c) : buf_(c, 0.0f), cap_(c) {
    assert(c > 0 && "RingBuffer capacity must be > 0");
}
void RingBuffer::write(const float* d, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        buf_[head_] = d[i];
        head_ = (head_ + 1) % cap_;
    }
    filled_ = std::min(filled_ + n, cap_);
}
std::vector<float> RingBuffer::lastN(size_t n) const {
    if (n > filled_) n = filled_;
    std::vector<float> out(n);
    for (size_t i = 0; i < n; ++i)
        out[n - 1 - i] = buf_[(head_ + cap_ - 1 - i) % cap_];
    return out;
}
std::vector<float> RingBuffer::extract(size_t pre, size_t post) const {
    return lastN(pre + post);
}
size_t RingBuffer::extractInto(size_t pre, size_t post, float* out) const {
    assert(pre <= SIZE_MAX - post);
    size_t count = std::min(pre + post, filled_);
    for (size_t i = 0; i < count; ++i)
        out[count - 1 - i] = buf_[(head_ + cap_ - 1 - i) % cap_];
    return count;
}
}

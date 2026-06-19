#include "buffer/RingBuffer.h"
namespace buffer {
RingBuffer::RingBuffer(size_t c) : buf_(c, 0.0f), cap_(c) {}
void RingBuffer::write(const float* d, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        buf_[head_] = d[i];
        head_ = (head_ + 1) % cap_;
        if (filled_ < cap_) ++filled_;
    }
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
}

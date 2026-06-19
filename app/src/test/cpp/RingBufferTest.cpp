#include <gtest/gtest.h>
#include <vector>
#include "buffer/RingBuffer.h"

TEST(RingBuffer, OverwritesOldestWhenFull) {
    buffer::RingBuffer rb(4);
    float a[] = {1,2,3,4,5,6};
    rb.write(a, 6);              // keeps last 4: 3,4,5,6
    std::vector<float> out = rb.lastN(4);
    ASSERT_EQ(out.size(), 4u);
    EXPECT_FLOAT_EQ(out[0], 3); EXPECT_FLOAT_EQ(out[3], 6);
}
TEST(RingBuffer, ExtractPreAndPost) {
    buffer::RingBuffer rb(10);
    float pre[] = {1,2,3,4,5};
    rb.write(pre, 5);
    float post[] = {6,7,8};
    rb.write(post, 3);
    std::vector<float> seg = rb.extract(/*pre*/5, /*post*/3);
    ASSERT_EQ(seg.size(), 8u);
    EXPECT_FLOAT_EQ(seg.front(), 1); EXPECT_FLOAT_EQ(seg.back(), 8);
}
TEST(RingBuffer, LastNClampsToFilled) {
    buffer::RingBuffer rb(10);
    float a[] = {1,2,3};
    rb.write(a, 3);
    std::vector<float> out = rb.lastN(8);   // only 3 available
    ASSERT_EQ(out.size(), 3u);
    EXPECT_FLOAT_EQ(out[0], 1); EXPECT_FLOAT_EQ(out[2], 3);
}

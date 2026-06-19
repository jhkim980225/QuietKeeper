#include <gtest/gtest.h>
#include "dsp/Decibel.h"
TEST(Sanity, HarnessRuns) { EXPECT_DOUBLE_EQ(dsp::sanity(), 42.0); }

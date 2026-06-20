package com.quietkeeper.app.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeTest {
    @Test fun nullHeader_returnsNull() {
        assertNull(parseRange(null, 1000))
    }

    @Test fun explicitStartEnd() {
        assertEquals(0L..99L, parseRange("bytes=0-99", 1000))
    }

    @Test fun openEnded_clampsToTotal() {
        assertEquals(100L..999L, parseRange("bytes=100-", 1000))
    }

    @Test fun suffix_lastNBytes() {
        assertEquals(800L..999L, parseRange("bytes=-200", 1000))
    }

    @Test fun endBeyondTotal_clamps() {
        assertEquals(500L..999L, parseRange("bytes=500-100000", 1000))
    }

    @Test fun suffixLargerThanTotal_clampsToZero() {
        assertEquals(0L..999L, parseRange("bytes=-5000", 1000))
    }

    @Test fun invalid_returnsNull() {
        assertNull(parseRange("", 1000))
        assertNull(parseRange("bytes=", 1000))
        assertNull(parseRange("bytes=abc-def", 1000))
        assertNull(parseRange("items=0-99", 1000))           // wrong unit
        assertNull(parseRange("bytes=1000-1100", 1000))      // start >= total
        assertNull(parseRange("bytes=500-100", 1000))        // end < start
        assertNull(parseRange("bytes=0-50,60-90", 1000))     // multi-range unsupported
        assertNull(parseRange("bytes=-0", 1000))             // zero suffix
    }

    @Test fun zeroTotal_returnsNull() {
        assertNull(parseRange("bytes=0-10", 0))
    }
}

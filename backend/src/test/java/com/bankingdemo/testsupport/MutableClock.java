package com.bankingdemo.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can pin to an instant; with nothing pinned it behaves like the real UTC clock. */
public class MutableClock extends Clock {

    private volatile Instant pinned;

    public void pin(Instant instant) {
        this.pinned = instant;
    }

    public void reset() {
        this.pinned = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        Instant p = pinned;
        return p != null ? p : Instant.now();
    }
}

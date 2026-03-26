package com.example.tasktracker.emailsender.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class MutableClock extends Clock {
    private Instant now;
    public MutableClock(Instant start) { this.now = start; }
    @Override public Instant instant() { return now; }
    @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    public void fastForward(Duration d) { now = now.plus(d); }
}

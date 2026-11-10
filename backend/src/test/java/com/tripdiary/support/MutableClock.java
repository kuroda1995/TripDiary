package com.tripdiary.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** テスト用の時刻を進められる Clock。 */
public class MutableClock extends Clock {

    private Instant now;
    private final ZoneId zone;

    public MutableClock(Instant now) {
        this(now, ZoneOffset.UTC);
    }

    private MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public static MutableClock at(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    /** 同じ時刻でタイムゾーンだけ異なる Clock を返す（以降の advance は共有しない）。 */
    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }
}

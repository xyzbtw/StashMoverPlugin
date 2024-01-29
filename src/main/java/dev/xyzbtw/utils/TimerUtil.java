package dev.xyzbtw.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TimerUtil {
    private final AtomicLong time = new AtomicLong();
    public TimerUtil() {
        time.set(-1L);
    }
    public void reset() {
        time.set(System.nanoTime());
    }
    public long getPassedTimeMs() {
        return getMs(System.nanoTime() - time.get());
    }
    public static long getMs(final long time) {
        return TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS);
    }

}

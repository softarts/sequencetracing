package com.zhourui.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * A consumer for container output that buffers lines in a {@link java.util.concurrent.BlockingDeque} and enables tests
 * to wait for a matching condition.
 */
public class WaitingConsumer extends BaseConsumer<WaitingConsumer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaitingConsumer.class);

    private LinkedBlockingDeque<OutputFrame> frames = new LinkedBlockingDeque<>();

    private ArrayList<ArrayList<String>> matchAll = new ArrayList<>();

    public ArrayList<ArrayList<String>> getMatchAll() {
        return matchAll;
    }

    @Override
    public void accept(OutputFrame frame) {
        frames.add(frame);
    }

    // times = -1 means until end of logs
    public void waitUntilMatchTimes(Pattern pattern, long limit, TimeUnit limitUnit, int times)  throws TimeoutException {
        long expiry = limitUnit.toMillis(limit) + System.currentTimeMillis();
        int numberOfMatches = 0;
        matchAll.clear();
        while (System.currentTimeMillis() < expiry) {
            try {
                final OutputFrame frame = frames.pollFirst(100, TimeUnit.MILLISECONDS);

                if (frame != null) {
                    LOGGER.debug("{}: {}", frame.getType(), frame.getUtf8StringWithoutLineEnding());
                    String text = frame.getUtf8String();//.matches("(?s)" + regEx);

                    Matcher m = pattern.matcher(text);

                    if (m.find()) {
                        ArrayList<String> items = new ArrayList<>();
                        for (int i=1; i<=m.groupCount();i++) {
                            items.add(m.group(i));
                        }
                        items.add(text);
                        matchAll.add(items);
                        if (times!=-1 && ++numberOfMatches == times) {
                            LOGGER.info("already match %d times, return", times);
                            return;
                        }
                    }
                }

                if (frames.isEmpty()) {
                    // sleep for a moment to avoid excessive CPU spinning
                    Thread.sleep(10L);
                }

                if (frame == OutputFrame.END) {
                    return;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // did not return before expiry was reached
        throw new TimeoutException();
    }
}
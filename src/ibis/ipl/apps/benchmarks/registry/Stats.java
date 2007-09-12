package ibis.ipl.apps.benchmarks.registry;

import ibis.ipl.IbisIdentifier;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class Stats implements Serializable {

    private static final long serialVersionUID = 1L;

    private class Point implements Serializable {
        
        private static final long serialVersionUID = 1L;

        private final long time;

        private final int value;

        Point(long time, int value) {
            this.time = time;
            this.value = value;
        }

        long getTime() {
            return time;
        }

        int getValue() {
            return value;
        }

        public String toString() {
            return "(" + time + ", " + value + ")";
        }

    }

    private final IbisIdentifier ibis;

    private final List<Point> data;

    private final long start;

    Stats(IbisIdentifier ibis, long start) {
        this.ibis = ibis;
        this.start = start;

        start = System.currentTimeMillis();
        data = new LinkedList<Point>();
    }

    IbisIdentifier getIbis() {
        return ibis;
    }

    long currentTime() {
        return System.currentTimeMillis() - start;
    }

    synchronized void update(int value) {
        data.add(new Point(System.currentTimeMillis() - start, value));
    }

    synchronized int valueAt(long time) {
        int lastValue = 0;

        for (Point point : data) {
            if (point.time > time) {
                return lastValue;
            }
            lastValue = point.value;
        }
        return lastValue;
    }

    public synchronized String toString() {
        String result = "stats: ";
        for (Point point : data) {
            result += point + " ";
        }
        return result;
    }

}

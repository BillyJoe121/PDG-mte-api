package co.edu.icesi.pdg.mte.common;

import org.springframework.stereotype.Component;

@Component
public class SqlQueryCounter {
    private final ThreadLocal<Counter> current = new ThreadLocal<>();

    public void begin() {
        current.set(new Counter());
    }

    public void increment() {
        Counter counter = current.get();
        if (counter != null) {
            counter.increment();
        }
    }

    public int count() {
        Counter counter = current.get();
        return counter == null ? 0 : counter.count();
    }

    public void clear() {
        current.remove();
    }

    private static final class Counter {
        private int count;

        void increment() {
            count++;
        }

        int count() {
            return count;
        }
    }
}

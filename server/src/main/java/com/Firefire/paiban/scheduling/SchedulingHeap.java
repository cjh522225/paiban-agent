package com.Firefire.paiban.scheduling;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

public class SchedulingHeap {

    private final PriorityQueue<StaffEntry> heap = new PriorityQueue<>();
    private final Map<Long, StaffEntry> index = new HashMap<>();
    private final Map<Long, Long> currentCounts = new HashMap<>();

    public static class StaffEntry implements Comparable<StaffEntry> {
        final long userId;
        final long count;
        final LocalDate lastPicked;
        final int pickOrder;
        final String gender;
        final Long dormitoryId;
        final boolean isLocal;

        public StaffEntry(long userId, long count, LocalDate lastPicked, int pickOrder,
                          String gender, Long dormitoryId, boolean isLocal) {
            this.userId = userId;
            this.count = count;
            this.lastPicked = lastPicked;
            this.pickOrder = pickOrder;
            this.gender = gender;
            this.dormitoryId = dormitoryId;
            this.isLocal = isLocal;
        }

        @Override
        public int compareTo(StaffEntry o) {
            int cmp = Long.compare(this.count, o.count);
            if (cmp != 0) return cmp;
            if (this.isLocal && !o.isLocal) return -1;
            if (!this.isLocal && o.isLocal) return 1;
            if (this.lastPicked != null && o.lastPicked != null) {
                cmp = this.lastPicked.compareTo(o.lastPicked);
                if (cmp != 0) return cmp;
            } else if (this.lastPicked != null) {
                return 1;
            } else if (o.lastPicked != null) {
                return -1;
            }
            return Integer.compare(this.pickOrder, o.pickOrder);
        }
    }

    public void clear() {
        heap.clear();
        index.clear();
        currentCounts.clear();
    }

    public void addUser(long userId, long count, LocalDate lastPicked, int pickOrder,
                        String gender, Long dormitoryId, boolean isLocal) {
        StaffEntry entry = new StaffEntry(userId, count, lastPicked, pickOrder,
                gender, dormitoryId, isLocal);
        heap.offer(entry);
        index.put(userId, entry);
        currentCounts.put(userId, count);
    }

    public Long pickMin(Predicate<Long> filter) {
        List<StaffEntry> skipped = new ArrayList<>();
        Long result = null;
        while (!heap.isEmpty()) {
            StaffEntry top = heap.peek();
            if (top.count != currentCounts.getOrDefault(top.userId, -1L)) {
                heap.poll();
                continue;
            }
            if (!filter.test(top.userId)) {
                skipped.add(heap.poll());
                continue;
            }
            result = heap.poll().userId;
            currentCounts.remove(result);
            index.remove(result);
            break;
        }
        skipped.forEach(heap::offer);
        return result;
    }

    public void increment(long userId) {
        StaffEntry old = index.get(userId);
        if (old == null) return;
        long newCount = old.count + 1;
        currentCounts.put(userId, newCount);
        StaffEntry updated = new StaffEntry(userId, newCount, old.lastPicked,
                old.pickOrder + 1, old.gender, old.dormitoryId, old.isLocal);
        heap.offer(updated);
        index.put(userId, updated);
    }

    public boolean isEmpty() {
        return index.isEmpty();
    }
}

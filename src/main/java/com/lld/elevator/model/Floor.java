package com.lld.elevator.model;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Floor implements Comparable<Floor> {
    // Flyweight-pattern
    private static final Map<Integer, Floor> CACHE = new ConcurrentHashMap<>();

    private final int id;

    private Floor(int id) {
        this.id = id;
    }

    public static Floor of(int id) {
        return CACHE.computeIfAbsent(id, Floor::new);
    }

    public int getId() {
        return id;
    }

    @Override
    public int compareTo(Floor other) {
        return Integer.compare(this.id, other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Floor floor = (Floor) o;
        return id == floor.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Floor " + id;
    }
}

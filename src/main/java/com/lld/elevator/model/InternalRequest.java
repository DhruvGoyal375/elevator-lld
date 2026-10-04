package com.lld.elevator.model;

import java.util.Objects;

public class InternalRequest {
    private final Floor destinationFloor;

    public InternalRequest(Floor destinationFloor) {
        this.destinationFloor = Objects.requireNonNull(destinationFloor, "destinationFloor cannot be null");
    }

    public Floor getDestinationFloor() {
        return destinationFloor;
    }

    @Override
    public String toString() {
        return String.format("[InternalRequest - destinationFloor: %s]", destinationFloor);
    }
}

package com.lld.elevator.model;

import com.lld.elevator.enums.Direction;
import java.util.Objects;

public class ExternalRequest {
    private final Direction direction;
    private final Floor sourceFloor;

    public ExternalRequest(Direction direction, Floor sourceFloor) {
        this.direction = Objects.requireNonNull(direction, "direction cannot be null");
        this.sourceFloor = Objects.requireNonNull(sourceFloor, "sourceFloor cannot be null");
        if (direction == Direction.IDLE) {
            throw new IllegalArgumentException("Direction cannot be IDLE for an external hall request");
        }
    }

    public Floor getSourceFloor() {
        return sourceFloor;
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    public String toString() {
        return String.format("[ExternalRequest - sourceFloor: %s, direction: %s]", sourceFloor, direction);
    }
}

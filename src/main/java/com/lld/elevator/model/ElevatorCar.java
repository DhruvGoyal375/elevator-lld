package com.lld.elevator.model;

import com.lld.elevator.enums.Direction;
import com.lld.elevator.enums.DoorState;
import com.lld.elevator.enums.ElevatorState;
import java.util.Objects;

public class ElevatorCar {
    private final int id;
    private Floor currentFloor;
    private Direction currentDirection;
    private ElevatorState state;
    private DoorState doorState;
    private final int capacity;

    public ElevatorCar(int id, Floor initialFloor, int capacity) {
        if (id <= 0) {
            throw new IllegalArgumentException("Elevator car id must be positive: " + id);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Elevator car capacity must be positive: " + capacity);
        }
        this.id = id;
        this.currentFloor = Objects.requireNonNull(initialFloor, "Initial floor cannot be null");
        this.currentDirection = Direction.IDLE;
        this.state = ElevatorState.STOPPED;
        this.doorState = DoorState.CLOSED;
        this.capacity = capacity;
    }

    public void openDoors() {
        this.doorState = DoorState.OPEN;
        System.out.printf("    [ElevatorCar %d] Doors OPENED at %s%n", id, currentFloor);
    }

    public void closeDoors() {
        this.doorState = DoorState.CLOSED;
        System.out.printf("    [ElevatorCar %d] Doors CLOSED at %s%n", id, currentFloor);
    }

    public int getId() {
        return id;
    }

    public Floor getCurrentFloor() {
        return currentFloor;
    }

    public void setCurrentFloor(Floor currentFloor) {
        this.currentFloor = Objects.requireNonNull(currentFloor, "Current floor cannot be null");
    }

    public Direction getCurrentDirection() {
        return currentDirection;
    }

    public void setCurrentDirection(Direction currentDirection) {
        this.currentDirection = Objects.requireNonNull(currentDirection, "Current direction cannot be null");
    }

    public ElevatorState getState() {
        return state;
    }

    public void setState(ElevatorState state) {
        this.state = Objects.requireNonNull(state, "Elevator state cannot be null");
    }

    public DoorState getDoorState() {
        return doorState;
    }

    public void setDoorState(DoorState doorState) {
        this.doorState = Objects.requireNonNull(doorState, "Door state cannot be null");
    }

    public int getCapacity() {
        return capacity;
    }
}

package com.lld.elevator.observer;

import com.lld.elevator.enums.Direction;
import com.lld.elevator.enums.ElevatorState;
import com.lld.elevator.model.Floor;

public interface CarStatusListener {
    public void onCarStatusUpdate(
            int carId, Floor currentFloor, Direction direction, ElevatorState state, int pendingStops);
}

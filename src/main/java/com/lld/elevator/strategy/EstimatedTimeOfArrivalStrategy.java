package com.lld.elevator.strategy;

import com.lld.elevator.enums.Direction;
import com.lld.elevator.enums.ElevatorState;
import com.lld.elevator.model.ElevatorCar;
import com.lld.elevator.model.ElevatorController;
import com.lld.elevator.model.ExternalRequest;
import java.util.List;
import java.util.Objects;

public class EstimatedTimeOfArrivalStrategy implements ElevatorSelectionStrategy {
    private static final int SECONDS_PER_FLOOR = 2;
    private static final int STOP_DWELL_SECONDS = 5;

    @Override
    public ElevatorController selectElevator(List<ElevatorController> controllers, ExternalRequest request) {
        if (controllers == null || controllers.isEmpty()) {
            throw new IllegalArgumentException("Controllers list cannot be null or empty");
        }
        Objects.requireNonNull(request, "External request cannot be null");

        ElevatorController bestChoice = null;
        int lowestEta = Integer.MAX_VALUE;

        for (ElevatorController controller : controllers) {
            ElevatorCar car = controller.getElevator();
            if (car.getState() == ElevatorState.OUT_OF_SERVICE) {
                continue;
            }

            int eta = computeEta(car, controller, request);
            if (eta < lowestEta) {
                lowestEta = eta;
                bestChoice = controller;
            }
        }

        return bestChoice; // null if all elevators are out of service
    }

    private int computeEta(ElevatorCar car, ElevatorController controller, ExternalRequest request) {
        int currentId = car.getCurrentFloor().getId();
        int targetId = request.getSourceFloor().getId();
        Direction reqDir = request.getDirection();
        Direction carDir = car.getCurrentDirection();

        // Case 1: Idle Elevator
        if (carDir == Direction.IDLE) {
            return Math.abs(currentId - targetId) * SECONDS_PER_FLOOR;
        }

        // Dwell time if the elevator is currently stopped at currentId and must finish cycle before
        // moving
        int currentStopDwell =
                (car.getState() == ElevatorState.STOPPED && currentId != targetId) ? STOP_DWELL_SECONDS : 0;

        // Case 2: Elevator is moving in the requested direction and hasn't passed the target floor
        // yet (co-directional pickup)
        if (carDir == reqDir) {
            if (carDir == Direction.UP && targetId >= currentId) {
                int intermediateStops = controller.countStopsStrictlyBetween(currentId, targetId, Direction.UP);
                return ((targetId - currentId) * SECONDS_PER_FLOOR)
                        + (intermediateStops * STOP_DWELL_SECONDS)
                        + currentStopDwell;
            }
            if (carDir == Direction.DOWN && targetId <= currentId) {
                int intermediateStops = controller.countStopsStrictlyBetween(targetId, currentId, Direction.DOWN);
                return ((currentId - targetId) * SECONDS_PER_FLOOR)
                        + (intermediateStops * STOP_DWELL_SECONDS)
                        + currentStopDwell;
            }
        }

        // Case 3: Ineligible for direct pickup on current leg (opposite direction or car already
        // passed target)
        int distance;
        if (carDir == Direction.UP) {
            int effectivePeak = Math.max(controller.getHighestPendingFloor(), targetId);
            distance = (effectivePeak - currentId) + (effectivePeak - targetId);
        } else {
            int effectiveValley = Math.min(controller.getLowestPendingFloor(), targetId);
            distance = (currentId - effectiveValley) + (targetId - effectiveValley);
        }

        int remainingStops = controller.getPendingStopsCount();
        return (distance * SECONDS_PER_FLOOR) + (remainingStops * STOP_DWELL_SECONDS) + currentStopDwell;
    }
}

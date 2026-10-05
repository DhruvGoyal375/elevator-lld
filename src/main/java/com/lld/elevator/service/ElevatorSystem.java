package com.lld.elevator.service;

import com.lld.elevator.enums.Direction;
import com.lld.elevator.enums.ElevatorState;
import com.lld.elevator.model.ElevatorController;
import com.lld.elevator.model.ExternalRequest;
import com.lld.elevator.model.Floor;
import com.lld.elevator.observer.CarStatusListener;
import com.lld.elevator.strategy.ElevatorSelectionStrategy;
import java.util.List;
import java.util.Objects;

public class ElevatorSystem implements CarStatusListener {
    private final int minFloor;
    private final int maxFloor;
    private final List<ElevatorController> controllers;
    private final ElevatorSelectionStrategy strategy;

    public ElevatorSystem(
            int minFloor, int maxFloor, List<ElevatorController> controllers, ElevatorSelectionStrategy strategy) {
        if (minFloor >= maxFloor) {
            throw new IllegalArgumentException(
                    String.format("minFloor (%d) must be strictly less than maxFloor (%d)", minFloor, maxFloor));
        }
        if (controllers == null || controllers.isEmpty()) {
            throw new IllegalArgumentException("Controllers list cannot be null or empty");
        }
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.controllers = List.copyOf(controllers); // creates an unmodifiable, null-safe snapshot of controllers
        this.strategy = Objects.requireNonNull(strategy, "Strategy cannot be null");

        for (ElevatorController controller : this.controllers) {
            validateFloor(controller.getElevator().getCurrentFloor());
            controller.registerListener(this);
        }
    }

    public ElevatorSystem(List<ElevatorController> controllers, ElevatorSelectionStrategy strategy) {
        this(0, 10, controllers, strategy);
    }

    public void validateFloor(Floor floor) {
        if (floor == null) {
            throw new IllegalArgumentException("Floor cannot be null");
        }
        if (floor.getId() < minFloor || floor.getId() > maxFloor) {
            throw new IllegalArgumentException(
                    String.format("Floor %d is outside building bounds [%d, %d]", floor.getId(), minFloor, maxFloor));
        }
    }

    public int getMinFloor() {
        return minFloor;
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    public List<ElevatorController> getControllers() {
        return controllers;
    }

    public synchronized void handleExternalRequest(ExternalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("External request cannot be null");
        }
        validateFloor(request.getSourceFloor());
        if (request.getSourceFloor().getId() == maxFloor && request.getDirection() == Direction.UP) {
            throw new IllegalArgumentException("Cannot request UP from top floor " + maxFloor);
        }
        if (request.getSourceFloor().getId() == minFloor && request.getDirection() == Direction.DOWN) {
            throw new IllegalArgumentException("Cannot request DOWN from bottom floor " + minFloor);
        }

        System.out.printf("%n[ElevatorSystem] Evaluating dispatch for %s%n", request);
        ElevatorController bestCarController = strategy.selectElevator(controllers, request);
        if (bestCarController == null) {
            System.out.printf(
                    "  >> [ElevatorSystem] Warning: No available operational elevator to service" + " %s%n", request);
            return;
        }
        bestCarController.assignExternalPickup(request);
    }

    @Override
    public void onCarStatusUpdate(
            int carId, Floor currentFloor, Direction direction, ElevatorState state, int pendingStops) {
        System.out.printf(
                "  >> [ElevatorSystem Telemetry] Car %d is at %s, Dir: %s, State: %s, Stops: %d%n",
                carId, currentFloor, direction, state, pendingStops);
    }

    public synchronized void stepSimulation() {
        for (ElevatorController controller : controllers) {
            if (controller.hasPendingRequests() || controller.getElevator().getState() == ElevatorState.MOVING) {
                controller.step();
            }
        }
    }

    public boolean hasActiveRequests() {
        return controllers.stream()
                .anyMatch(c -> c.hasPendingRequests() || c.getElevator().getState() == ElevatorState.MOVING);
    }
}

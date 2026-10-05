package com.lld.elevator.model;

import com.lld.elevator.enums.Direction;
import com.lld.elevator.enums.ElevatorState;
import com.lld.elevator.observer.CarStatusListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

public class ElevatorController {
    private final ElevatorCar elevator;
    private final PriorityQueue<Floor> upStops;
    private final PriorityQueue<Floor> downStops;
    private final Set<Floor> internalDestinations;
    private final List<CarStatusListener> listeners;
    // prevent opposite-direction calls on the same floor from collapsing
    private final Set<Floor> upPickupRequests;
    private final Set<Floor> downPickupRequests;
    // 4-queue partitioning: defer calls behind the car to prevent stalls in min/max heaps
    private final Set<Floor> deferredUpStops;
    private final Set<Floor> deferredDownStops;

    public ElevatorController(ElevatorCar elevator) {
        this.elevator = Objects.requireNonNull(elevator, "Elevator car cannot be null");
        this.upStops = new PriorityQueue<>(); // Shortest Floor First
        this.downStops = new PriorityQueue<>(Collections.reverseOrder()); // Largest Floor First
        this.upPickupRequests = new HashSet<>();
        this.downPickupRequests = new HashSet<>();
        this.deferredUpStops = new HashSet<>();
        this.deferredDownStops = new HashSet<>();
        this.internalDestinations = new HashSet<>();
        this.listeners = new ArrayList<>();
    }

    public void registerListener(CarStatusListener listener) {
        listeners.add(listener);
    }

    public void notifyListeners() {
        for (CarStatusListener listener : listeners) {
            listener.onCarStatusUpdate(
                    elevator.getId(),
                    elevator.getCurrentFloor(),
                    elevator.getCurrentDirection(),
                    elevator.getState(),
                    getPendingStopsCount());
        }
    }

    public synchronized void handleInternalRequest(InternalRequest internalRequest) {
        if (internalRequest == null) {
            throw new IllegalArgumentException("Internal request cannot be null");
        }
        Floor destinationFloor = internalRequest.getDestinationFloor();
        System.out.printf(
                "[ElevatorController %d] Received internal cab call to %s%n", elevator.getId(), destinationFloor);

        Floor currentFloor = elevator.getCurrentFloor();
        if (destinationFloor.equals(currentFloor) && elevator.getState() == ElevatorState.STOPPED) {
            elevator.openDoors();
            elevator.closeDoors();
            return;
        }

        internalDestinations.add(destinationFloor);

        if (destinationFloor.compareTo(currentFloor) > 0) {
            if (!upStops.contains(destinationFloor)) {
                upStops.offer(destinationFloor);
            }
        } else {
            if (!downStops.contains(destinationFloor)) {
                downStops.offer(destinationFloor);
            }
        }

        if (elevator.getCurrentDirection() == Direction.IDLE) {
            elevator.setCurrentDirection(destinationFloor.compareTo(currentFloor) > 0 ? Direction.UP : Direction.DOWN);
        }
        notifyListeners();
    }

    public synchronized void assignExternalPickup(ExternalRequest externalRequest) {
        if (externalRequest == null) {
            throw new IllegalArgumentException("External request cannot be null");
        }
        Floor sourceFloor = externalRequest.getSourceFloor();
        Direction direction = externalRequest.getDirection();
        System.out.printf(
                "[ElevatorController %d] Assigned pickup at %s to move %s%n", elevator.getId(), sourceFloor, direction);

        if (direction == Direction.UP) {
            upPickupRequests.add(sourceFloor);
        } else if (direction == Direction.DOWN) {
            downPickupRequests.add(sourceFloor);
        }

        Floor currentFloor = elevator.getCurrentFloor();

        if (sourceFloor.equals(currentFloor) && elevator.getState() == ElevatorState.STOPPED) {
            if (elevator.getCurrentDirection() == Direction.IDLE || elevator.getCurrentDirection() == direction) {
                elevator.setCurrentDirection(direction);
                handleArrivalAtFloor(sourceFloor, direction);
                return;
            }
        }

        schedulePickupStop(sourceFloor, direction, currentFloor);
        notifyListeners();
    }

    private void schedulePickupStop(Floor sourceFloor, Direction reqDir, Floor currentFloor) {
        if (reqDir == Direction.UP) {
            if (elevator.getCurrentDirection() == Direction.UP && sourceFloor.compareTo(currentFloor) < 0) {
                // Request is behind car on current UP sweep: defer until subsequent cycle
                deferredUpStops.add(sourceFloor);
            } else {
                if (!upStops.contains(sourceFloor)) {
                    upStops.offer(sourceFloor);
                }
                if (sourceFloor.compareTo(currentFloor) < 0) {
                    int lowestDown = getLowestStopInQueue(downStops, currentFloor.getId());
                    if (sourceFloor.getId() < lowestDown || downStops.isEmpty()) {
                        pruneTurnaroundStopsInDownQueue(sourceFloor.getId());
                        if (!downStops.contains(sourceFloor)) {
                            downStops.offer(sourceFloor);
                        }
                    }
                }
            }
        } else if (reqDir == Direction.DOWN) {
            if (elevator.getCurrentDirection() == Direction.DOWN && sourceFloor.compareTo(currentFloor) > 0) {
                // Request is behind car on current DOWN sweep: defer until subsequent cycle
                deferredDownStops.add(sourceFloor);
            } else {
                if (!downStops.contains(sourceFloor)) {
                    downStops.offer(sourceFloor);
                }
                if (sourceFloor.compareTo(currentFloor) > 0) {
                    int highestUp = getHighestStopInQueue(upStops, currentFloor.getId());
                    if (sourceFloor.getId() > highestUp || upStops.isEmpty()) {
                        pruneTurnaroundStopsInUpQueue(sourceFloor.getId());
                        if (!upStops.contains(sourceFloor)) {
                            upStops.offer(sourceFloor);
                        }
                    }
                }
            }
        }

        if (elevator.getCurrentDirection() == Direction.IDLE) {
            if (sourceFloor.compareTo(currentFloor) > 0) {
                elevator.setCurrentDirection(Direction.UP);
            } else if (sourceFloor.compareTo(currentFloor) < 0) {
                elevator.setCurrentDirection(Direction.DOWN);
            } else {
                elevator.setCurrentDirection(reqDir);
            }
        }
    }

    private int getHighestStopInQueue(PriorityQueue<Floor> queue, int defaultVal) {
        int max = defaultVal;
        for (Floor f : queue) {
            max = Math.max(max, f.getId());
        }
        return max;
    }

    private int getLowestStopInQueue(PriorityQueue<Floor> queue, int defaultVal) {
        int min = defaultVal;
        for (Floor f : queue) {
            min = Math.min(min, f.getId());
        }
        return min;
    }

    private void pruneTurnaroundStopsInUpQueue(int newPeakFloor) {
        upStops.removeIf(f -> f.getId() < newPeakFloor
                && !internalDestinations.contains(f)
                && !upPickupRequests.contains(f)
                && !deferredUpStops.contains(f));
    }

    private void pruneTurnaroundStopsInDownQueue(int newValleyFloor) {
        downStops.removeIf(f -> f.getId() > newValleyFloor
                && !internalDestinations.contains(f)
                && !downPickupRequests.contains(f)
                && !deferredDownStops.contains(f));
    }

    public synchronized void step() {
        if (elevator.getCurrentDirection() == Direction.UP) {
            processUpQueue();
        } else if (elevator.getCurrentDirection() == Direction.DOWN) {
            processDownQueue();
        } else {
            if (!upStops.isEmpty()) {
                elevator.setCurrentDirection(Direction.UP);
                processUpQueue();
            } else if (!downStops.isEmpty()) {
                elevator.setCurrentDirection(Direction.DOWN);
                processDownQueue();
            }
        }
    }

    private void processUpQueue() {
        if (!upStops.isEmpty()) {
            Floor nextFloor = upStops.peek();

            if (elevator.getCurrentFloor().compareTo(nextFloor) < 0) {
                elevator.setState(ElevatorState.MOVING);
                elevator.setCurrentFloor(Floor.of(elevator.getCurrentFloor().getId() + 1));
                System.out.printf(
                        "    [ElevatorCar %d] Moving UP -> %s%n", elevator.getId(), elevator.getCurrentFloor());
                notifyListeners();
            }

            if (elevator.getCurrentFloor().equals(nextFloor)) {
                upStops.poll(); // Evict only when reached
                handleArrivalAtFloor(nextFloor, Direction.UP);
            }
        } else {
            transitionFromUpSweep();
        }
    }

    private void processDownQueue() {
        if (!downStops.isEmpty()) {
            Floor nextFloor = downStops.peek();

            if (elevator.getCurrentFloor().compareTo(nextFloor) > 0) {
                elevator.setState(ElevatorState.MOVING);
                elevator.setCurrentFloor(Floor.of(elevator.getCurrentFloor().getId() - 1));
                System.out.printf(
                        "    [ElevatorCar %d] Moving DOWN -> %s%n", elevator.getId(), elevator.getCurrentFloor());
                notifyListeners();
            }

            if (elevator.getCurrentFloor().equals(nextFloor)) {
                downStops.poll(); // Evict only when reached
                handleArrivalAtFloor(nextFloor, Direction.DOWN);
            }
        } else {
            transitionFromDownSweep();
        }
    }

    private void transitionFromUpSweep() {
        // 1. Promote any deferred DOWN calls that arrived during previous downward passes
        if (!deferredDownStops.isEmpty()) {
            for (Floor f : deferredDownStops) {
                if (!downStops.contains(f)) {
                    downStops.offer(f);
                }
            }
            deferredDownStops.clear();
        }

        // 2. If downward stops or requests exist, reverse direction to DOWN
        if (!downStops.isEmpty() || !downPickupRequests.isEmpty()) {
            elevator.setCurrentDirection(Direction.DOWN);
            notifyListeners();
            return;
        }

        // 3. If no downward calls exist, check if there are deferred UP stops that were behind the car
        if (!deferredUpStops.isEmpty()) {
            for (Floor f : deferredUpStops) {
                if (!upStops.contains(f)) {
                    upStops.offer(f);
                }
            }
            deferredUpStops.clear();

            // To service UP stops located below the current peak position, travel down to the lowest one
            Floor lowestDeferred = upStops.peek();
            if (lowestDeferred != null && lowestDeferred.compareTo(elevator.getCurrentFloor()) < 0) {
                if (!downStops.contains(lowestDeferred)) {
                    downStops.offer(lowestDeferred);
                }
                elevator.setCurrentDirection(Direction.DOWN);
            } else {
                elevator.setCurrentDirection(Direction.UP);
            }
            notifyListeners();
            return;
        }

        // 4. No pending stops anywhere
        elevator.setCurrentDirection(Direction.IDLE);
        elevator.setState(ElevatorState.STOPPED);
        notifyListeners();
    }

    private void transitionFromDownSweep() {
        // 1. Promote any deferred UP calls that arrived during previous upward passes
        if (!deferredUpStops.isEmpty()) {
            for (Floor f : deferredUpStops) {
                if (!upStops.contains(f)) {
                    upStops.offer(f);
                }
            }
            deferredUpStops.clear();
        }

        // 2. If upward stops or requests exist, reverse direction to UP
        if (!upStops.isEmpty() || !upPickupRequests.isEmpty()) {
            elevator.setCurrentDirection(Direction.UP);
            notifyListeners();
            return;
        }

        // 3. If no upward calls exist, check if there are deferred DOWN stops that were behind the car
        if (!deferredDownStops.isEmpty()) {
            for (Floor f : deferredDownStops) {
                if (!downStops.contains(f)) {
                    downStops.offer(f);
                }
            }
            deferredDownStops.clear();

            // To service DOWN stops located above the current valley position, travel up to the highest one
            Floor highestDeferred = downStops.peek();
            if (highestDeferred != null && highestDeferred.compareTo(elevator.getCurrentFloor()) > 0) {
                if (!upStops.contains(highestDeferred)) {
                    upStops.offer(highestDeferred);
                }
                elevator.setCurrentDirection(Direction.UP);
            } else {
                elevator.setCurrentDirection(Direction.DOWN);
            }
            notifyListeners();
            return;
        }

        // 4. No pending stops anywhere
        elevator.setCurrentDirection(Direction.IDLE);
        elevator.setState(ElevatorState.STOPPED);
        notifyListeners();
    }

    private void handleArrivalAtFloor(Floor floor, Direction travelDir) {
        elevator.setState(ElevatorState.STOPPED);
        elevator.openDoors();

        if (internalDestinations.remove(floor)) {
            System.out.printf("    [ElevatorCar %d] Serviced internal drop-off at %s%n", elevator.getId(), floor);
        }

        if (travelDir == Direction.UP) {
            if (upPickupRequests.remove(floor)) {
                System.out.printf("    [ElevatorCar %d] Serviced UP pickup at %s%n", elevator.getId(), floor);
            }
            // If turning around at the peak floor, also service a pending DOWN pickup if present
            if (upStops.isEmpty() && downPickupRequests.contains(floor)) {
                downPickupRequests.remove(floor);
                downStops.remove(floor);
                elevator.setCurrentDirection(Direction.DOWN);
                System.out.printf(
                        "    [ElevatorCar %d] Serviced DOWN pickup at %s on turnaround (Direction" + " is now DOWN)%n",
                        elevator.getId(), floor);
            }
        } else if (travelDir == Direction.DOWN) {
            if (downPickupRequests.remove(floor)) {
                System.out.printf("    [ElevatorCar %d] Serviced DOWN pickup at %s%n", elevator.getId(), floor);
            }
            // If turning around at the valley floor, also service a pending UP pickup if present
            if (downStops.isEmpty() && upPickupRequests.contains(floor)) {
                upPickupRequests.remove(floor);
                upStops.remove(floor);
                elevator.setCurrentDirection(Direction.UP);
                System.out.printf(
                        "    [ElevatorCar %d] Serviced UP pickup at %s on turnaround (Direction is" + " now UP)%n",
                        elevator.getId(), floor);
            }
        }

        elevator.closeDoors();
        notifyListeners();
    }

    public synchronized boolean hasPendingRequests() {
        return !downStops.isEmpty()
                || !upStops.isEmpty()
                || !deferredUpStops.isEmpty()
                || !deferredDownStops.isEmpty()
                || !upPickupRequests.isEmpty()
                || !downPickupRequests.isEmpty()
                || !internalDestinations.isEmpty();
    }

    public synchronized int getPendingStopsCount() {
        return upStops.size() + downStops.size() + deferredUpStops.size() + deferredDownStops.size();
    }

    public synchronized int getHighestPendingFloor() {
        int max = elevator.getCurrentFloor().getId();
        for (Floor f : upStops) {
            max = Math.max(max, f.getId());
        }
        for (Floor f : downStops) {
            max = Math.max(max, f.getId());
        }
        for (Floor f : deferredUpStops) {
            max = Math.max(max, f.getId());
        }
        for (Floor f : deferredDownStops) {
            max = Math.max(max, f.getId());
        }
        for (Floor f : upPickupRequests) {
            max = Math.max(max, f.getId());
        }
        for (Floor f : downPickupRequests) {
            max = Math.max(max, f.getId());
        }
        return max;
    }

    public synchronized int getLowestPendingFloor() {
        int min = elevator.getCurrentFloor().getId();
        for (Floor f : upStops) {
            min = Math.min(min, f.getId());
        }
        for (Floor f : downStops) {
            min = Math.min(min, f.getId());
        }
        for (Floor f : deferredUpStops) {
            min = Math.min(min, f.getId());
        }
        for (Floor f : deferredDownStops) {
            min = Math.min(min, f.getId());
        }
        for (Floor f : upPickupRequests) {
            min = Math.min(min, f.getId());
        }
        for (Floor f : downPickupRequests) {
            min = Math.min(min, f.getId());
        }
        return min;
    }

    public synchronized int countStopsStrictlyBetween(int low, int high, Direction dir) {
        int count = 0;
        PriorityQueue<Floor> queue = (dir == Direction.UP) ? upStops : downStops;
        for (Floor f : queue) {
            if (f.getId() > low && f.getId() < high) {
                count++;
            }
        }
        return count;
    }

    public synchronized Set<Floor> getUpPickupRequests() {
        return Collections.unmodifiableSet(upPickupRequests);
    }

    public synchronized Set<Floor> getDownPickupRequests() {
        return Collections.unmodifiableSet(downPickupRequests);
    }

    public synchronized Set<Floor> getDeferredUpStops() {
        return Collections.unmodifiableSet(deferredUpStops);
    }

    public synchronized Set<Floor> getDeferredDownStops() {
        return Collections.unmodifiableSet(deferredDownStops);
    }

    public synchronized Set<Floor> getInternalDestinations() {
        return Collections.unmodifiableSet(internalDestinations);
    }

    public synchronized PriorityQueue<Floor> getDownStops() {
        return new PriorityQueue<>(downStops);
    }

    public synchronized PriorityQueue<Floor> getUpStops() {
        return new PriorityQueue<>(upStops);
    }

    public ElevatorCar getElevator() {
        return elevator;
    }
}

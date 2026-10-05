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
import java.util.TreeSet;

/**
 * Controller managing motion, scheduling, and request fulfillment for a single ElevatorCar.
 * Implements the LOOK/SCAN algorithm using three distinct request intent sets:
 * - cabStops: drop-offs requested from inside the car
 * - upPickupRequests: external hallway boarding requests wishing to travel UP
 * - downPickupRequests: external hallway boarding requests wishing to travel DOWN
 */
public class ElevatorController {
    private final ElevatorCar elevator;
    private final TreeSet<Floor> cabStops;
    private final TreeSet<Floor> upPickupRequests;
    private final TreeSet<Floor> downPickupRequests;
    private final List<CarStatusListener> listeners;

    public ElevatorController(ElevatorCar elevator) {
        this.elevator = Objects.requireNonNull(elevator, "Elevator car cannot be null");
        this.cabStops = new TreeSet<>();
        this.upPickupRequests = new TreeSet<>();
        this.downPickupRequests = new TreeSet<>();
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
        if (elevator.getState() == ElevatorState.OUT_OF_SERVICE) {
            System.out.printf(
                    "[ElevatorController %d] Rejected internal request: Car is OUT_OF_SERVICE%n", elevator.getId());
            return;
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

        cabStops.add(destinationFloor);

        if (elevator.getCurrentDirection() == Direction.IDLE) {
            elevator.setCurrentDirection(destinationFloor.compareTo(currentFloor) > 0 ? Direction.UP : Direction.DOWN);
        }
        notifyListeners();
    }

    public synchronized void assignExternalPickup(ExternalRequest externalRequest) {
        if (externalRequest == null) {
            throw new IllegalArgumentException("External request cannot be null");
        }
        if (elevator.getState() == ElevatorState.OUT_OF_SERVICE) {
            System.out.printf(
                    "[ElevatorController %d] Rejected external pickup: Car is OUT_OF_SERVICE%n", elevator.getId());
            return;
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

        if (elevator.getCurrentDirection() == Direction.IDLE) {
            if (sourceFloor.compareTo(currentFloor) > 0) {
                elevator.setCurrentDirection(Direction.UP);
            } else if (sourceFloor.compareTo(currentFloor) < 0) {
                elevator.setCurrentDirection(Direction.DOWN);
            } else {
                elevator.setCurrentDirection(direction);
            }
        }
        notifyListeners();
    }

    public synchronized void step() {
        if (elevator.getState() == ElevatorState.OUT_OF_SERVICE) {
            return;
        }

        Direction dir = elevator.getCurrentDirection();
        if (dir == Direction.IDLE) {
            pickInitialDirectionIfIdle();
            dir = elevator.getCurrentDirection();
            if (dir == Direction.IDLE) {
                elevator.setState(ElevatorState.STOPPED);
                return;
            }
        }

        if (dir == Direction.UP) {
            processUpStep();
        } else if (dir == Direction.DOWN) {
            processDownStep();
        }
    }

    private void processUpStep() {
        Floor current = elevator.getCurrentFloor();
        if (hasRequestsAbove(current)) {
            Floor nextFloor = Floor.of(current.getId() + 1);
            elevator.setState(ElevatorState.MOVING);
            elevator.setCurrentFloor(nextFloor);
            System.out.printf("    [ElevatorCar %d] Moving UP -> %s%n", elevator.getId(), elevator.getCurrentFloor());
            notifyListeners();

            if (shouldStopAtFloorMovingUp(nextFloor)) {
                handleArrivalAtFloor(nextFloor, Direction.UP);
            }
        } else {
            transitionFromUpSweep();
        }
    }

    private void processDownStep() {
        Floor current = elevator.getCurrentFloor();
        if (hasRequestsBelow(current)) {
            Floor nextFloor = Floor.of(current.getId() - 1);
            elevator.setState(ElevatorState.MOVING);
            elevator.setCurrentFloor(nextFloor);
            System.out.printf("    [ElevatorCar %d] Moving DOWN -> %s%n", elevator.getId(), elevator.getCurrentFloor());
            notifyListeners();

            if (shouldStopAtFloorMovingDown(nextFloor)) {
                handleArrivalAtFloor(nextFloor, Direction.DOWN);
            }
        } else {
            transitionFromDownSweep();
        }
    }

    private boolean shouldStopAtFloorMovingUp(Floor nextFloor) {
        if (cabStops.contains(nextFloor) || upPickupRequests.contains(nextFloor)) {
            return true;
        }
        // Peak turnaround: stop for DOWN pickup only if there are no higher requests anywhere
        return downPickupRequests.contains(nextFloor) && !hasRequestsAbove(nextFloor);
    }

    private boolean shouldStopAtFloorMovingDown(Floor nextFloor) {
        if (cabStops.contains(nextFloor) || downPickupRequests.contains(nextFloor)) {
            return true;
        }
        // Valley turnaround: stop for UP pickup only if there are no lower requests anywhere
        return upPickupRequests.contains(nextFloor) && !hasRequestsBelow(nextFloor);
    }

    private void handleArrivalAtFloor(Floor floor, Direction travelDir) {
        elevator.setState(ElevatorState.STOPPED);
        elevator.openDoors();

        if (cabStops.remove(floor)) {
            System.out.printf("    [ElevatorCar %d] Serviced internal drop-off at %s%n", elevator.getId(), floor);
        }

        if (travelDir == Direction.UP) {
            if (upPickupRequests.remove(floor)) {
                System.out.printf("    [ElevatorCar %d] Serviced UP pickup at %s%n", elevator.getId(), floor);
            }
            // Peak turnaround: if no higher requests exist, service a pending DOWN pickup if present
            if (!hasRequestsAbove(floor) && downPickupRequests.contains(floor)) {
                downPickupRequests.remove(floor);
                elevator.setCurrentDirection(Direction.DOWN);
                System.out.printf(
                        "    [ElevatorCar %d] Serviced DOWN pickup at %s on turnaround (Direction" + " is now DOWN)%n",
                        elevator.getId(), floor);
            }
        } else if (travelDir == Direction.DOWN) {
            if (downPickupRequests.remove(floor)) {
                System.out.printf("    [ElevatorCar %d] Serviced DOWN pickup at %s%n", elevator.getId(), floor);
            }
            // Valley turnaround: if no lower requests exist, service a pending UP pickup if present
            if (!hasRequestsBelow(floor) && upPickupRequests.contains(floor)) {
                upPickupRequests.remove(floor);
                elevator.setCurrentDirection(Direction.UP);
                System.out.printf(
                        "    [ElevatorCar %d] Serviced UP pickup at %s on turnaround (Direction is" + " now UP)%n",
                        elevator.getId(), floor);
            }
        }

        elevator.closeDoors();

        // Update direction if current sweep has finished and direction wasn't already flipped by turnaround
        Direction currentDir = elevator.getCurrentDirection();
        if (currentDir == Direction.UP && !hasRequestsAbove(floor)) {
            if (hasRequestsBelow(floor)) {
                elevator.setCurrentDirection(Direction.DOWN);
            } else {
                elevator.setCurrentDirection(Direction.IDLE);
                elevator.setState(ElevatorState.STOPPED);
            }
        } else if (currentDir == Direction.DOWN && !hasRequestsBelow(floor)) {
            if (hasRequestsAbove(floor)) {
                elevator.setCurrentDirection(Direction.UP);
            } else {
                elevator.setCurrentDirection(Direction.IDLE);
                elevator.setState(ElevatorState.STOPPED);
            }
        }

        notifyListeners();
    }

    private void transitionFromUpSweep() {
        Floor current = elevator.getCurrentFloor();
        if (hasRequestsBelow(current)) {
            elevator.setCurrentDirection(Direction.DOWN);
            notifyListeners();
            processDownStep();
        } else {
            elevator.setCurrentDirection(Direction.IDLE);
            elevator.setState(ElevatorState.STOPPED);
            notifyListeners();
        }
    }

    private void transitionFromDownSweep() {
        Floor current = elevator.getCurrentFloor();
        if (hasRequestsAbove(current)) {
            elevator.setCurrentDirection(Direction.UP);
            notifyListeners();
            processUpStep();
        } else {
            elevator.setCurrentDirection(Direction.IDLE);
            elevator.setState(ElevatorState.STOPPED);
            notifyListeners();
        }
    }

    private void pickInitialDirectionIfIdle() {
        Floor currentFloor = elevator.getCurrentFloor();
        if (hasRequestsAbove(currentFloor)) {
            if (hasRequestsBelow(currentFloor)) {
                Floor closestAbove = getClosestRequestAbove(currentFloor);
                Floor closestBelow = getClosestRequestBelow(currentFloor);
                int distAbove = closestAbove.getId() - currentFloor.getId();
                int distBelow = currentFloor.getId() - closestBelow.getId();
                elevator.setCurrentDirection(distAbove <= distBelow ? Direction.UP : Direction.DOWN);
            } else {
                elevator.setCurrentDirection(Direction.UP);
            }
        } else if (hasRequestsBelow(currentFloor)) {
            elevator.setCurrentDirection(Direction.DOWN);
        } else {
            if (cabStops.contains(currentFloor)) {
                handleArrivalAtFloor(currentFloor, Direction.IDLE);
            } else if (upPickupRequests.contains(currentFloor)) {
                elevator.setCurrentDirection(Direction.UP);
                handleArrivalAtFloor(currentFloor, Direction.UP);
            } else if (downPickupRequests.contains(currentFloor)) {
                elevator.setCurrentDirection(Direction.DOWN);
                handleArrivalAtFloor(currentFloor, Direction.DOWN);
            }
        }
    }

    private boolean hasRequestsAbove(Floor f) {
        return cabStops.higher(f) != null || upPickupRequests.higher(f) != null || downPickupRequests.higher(f) != null;
    }

    private boolean hasRequestsBelow(Floor f) {
        return cabStops.lower(f) != null || upPickupRequests.lower(f) != null || downPickupRequests.lower(f) != null;
    }

    private Floor getClosestRequestAbove(Floor f) {
        Floor best = null;
        Floor hCab = cabStops.higher(f);
        Floor hUp = upPickupRequests.higher(f);
        Floor hDown = downPickupRequests.higher(f);
        if (hCab != null) best = hCab;
        if (hUp != null && (best == null || hUp.compareTo(best) < 0)) best = hUp;
        if (hDown != null && (best == null || hDown.compareTo(best) < 0)) best = hDown;
        return best;
    }

    private Floor getClosestRequestBelow(Floor f) {
        Floor best = null;
        Floor lCab = cabStops.lower(f);
        Floor lUp = upPickupRequests.lower(f);
        Floor lDown = downPickupRequests.lower(f);
        if (lCab != null) best = lCab;
        if (lUp != null && (best == null || lUp.compareTo(best) > 0)) best = lUp;
        if (lDown != null && (best == null || lDown.compareTo(best) > 0)) best = lDown;
        return best;
    }

    public synchronized void takeOutOfService() {
        elevator.setState(ElevatorState.OUT_OF_SERVICE);
        elevator.setCurrentDirection(Direction.IDLE);
        notifyListeners();
    }

    public synchronized void returnToService() {
        elevator.setState(ElevatorState.STOPPED);
        elevator.setCurrentDirection(Direction.IDLE);
        notifyListeners();
    }

    public synchronized List<ExternalRequest> clearAndGetPendingExternalRequests() {
        List<ExternalRequest> pending = new ArrayList<>();
        for (Floor f : upPickupRequests) {
            pending.add(new ExternalRequest(Direction.UP, f));
        }
        for (Floor f : downPickupRequests) {
            pending.add(new ExternalRequest(Direction.DOWN, f));
        }
        upPickupRequests.clear();
        downPickupRequests.clear();
        cabStops.clear();
        return pending;
    }

    public synchronized boolean hasPendingRequests() {
        return !cabStops.isEmpty() || !upPickupRequests.isEmpty() || !downPickupRequests.isEmpty();
    }

    public synchronized int getPendingStopsCount() {
        Set<Floor> distinctStops = new HashSet<>(cabStops);
        distinctStops.addAll(upPickupRequests);
        distinctStops.addAll(downPickupRequests);
        return distinctStops.size();
    }

    public synchronized int getHighestPendingFloor() {
        int max = elevator.getCurrentFloor().getId();
        if (!cabStops.isEmpty()) max = Math.max(max, cabStops.last().getId());
        if (!upPickupRequests.isEmpty())
            max = Math.max(max, upPickupRequests.last().getId());
        if (!downPickupRequests.isEmpty())
            max = Math.max(max, downPickupRequests.last().getId());
        return max;
    }

    public synchronized int getLowestPendingFloor() {
        int min = elevator.getCurrentFloor().getId();
        if (!cabStops.isEmpty()) min = Math.min(min, cabStops.first().getId());
        if (!upPickupRequests.isEmpty())
            min = Math.min(min, upPickupRequests.first().getId());
        if (!downPickupRequests.isEmpty())
            min = Math.min(min, downPickupRequests.first().getId());
        return min;
    }

    public synchronized int countStopsStrictlyBetween(int low, int high, Direction dir) {
        Floor fLow = Floor.of(low);
        Floor fHigh = Floor.of(high);
        Set<Floor> stops = new HashSet<>(cabStops.subSet(fLow, false, fHigh, false));
        if (dir == Direction.UP) {
            stops.addAll(upPickupRequests.subSet(fLow, false, fHigh, false));
        } else if (dir == Direction.DOWN) {
            stops.addAll(downPickupRequests.subSet(fLow, false, fHigh, false));
        }
        return stops.size();
    }

    public synchronized PriorityQueue<Floor> getUpStops() {
        PriorityQueue<Floor> queue = new PriorityQueue<>();
        Floor current = elevator.getCurrentFloor();
        for (Floor f : cabStops.tailSet(current, true)) {
            queue.offer(f);
        }
        for (Floor f : upPickupRequests.tailSet(current, true)) {
            if (!queue.contains(f)) {
                queue.offer(f);
            }
        }
        Floor peakDown = getHighestDownRequest();
        if (peakDown != null && peakDown.compareTo(current) >= 0 && !hasRequestsAbove(peakDown)) {
            if (!queue.contains(peakDown)) {
                queue.offer(peakDown);
            }
        }
        return queue;
    }

    public synchronized PriorityQueue<Floor> getDownStops() {
        PriorityQueue<Floor> queue = new PriorityQueue<>(Collections.reverseOrder());
        Floor current = elevator.getCurrentFloor();
        for (Floor f : cabStops) {
            if (elevator.getCurrentDirection() == Direction.DOWN || f.compareTo(current) <= 0) {
                queue.offer(f);
            }
        }
        for (Floor f : downPickupRequests) {
            if (elevator.getCurrentDirection() == Direction.DOWN && f.compareTo(current) > 0) {
                continue; // deferred
            }
            if (!queue.contains(f)) {
                queue.offer(f);
            }
        }
        Floor valleyUp = getLowestUpRequest();
        if (valleyUp != null && valleyUp.compareTo(current) <= 0 && !hasRequestsBelow(valleyUp)) {
            if (!queue.contains(valleyUp)) {
                queue.offer(valleyUp);
            }
        }
        return queue;
    }

    public synchronized Set<Floor> getDeferredUpStops() {
        Floor current = elevator.getCurrentFloor();
        if (elevator.getCurrentDirection() == Direction.UP) {
            return Collections.unmodifiableSet(new HashSet<>(upPickupRequests.headSet(current, false)));
        }
        return Collections.emptySet();
    }

    public synchronized Set<Floor> getDeferredDownStops() {
        Floor current = elevator.getCurrentFloor();
        if (elevator.getCurrentDirection() == Direction.DOWN) {
            return Collections.unmodifiableSet(new HashSet<>(downPickupRequests.tailSet(current, false)));
        }
        return Collections.emptySet();
    }

    private Floor getHighestDownRequest() {
        return downPickupRequests.isEmpty() ? null : downPickupRequests.last();
    }

    private Floor getLowestUpRequest() {
        return upPickupRequests.isEmpty() ? null : upPickupRequests.first();
    }

    public synchronized Set<Floor> getUpPickupRequests() {
        return Collections.unmodifiableSet(new HashSet<>(upPickupRequests));
    }

    public synchronized Set<Floor> getDownPickupRequests() {
        return Collections.unmodifiableSet(new HashSet<>(downPickupRequests));
    }

    public synchronized Set<Floor> getInternalDestinations() {
        return Collections.unmodifiableSet(new HashSet<>(cabStops));
    }

    public ElevatorCar getElevator() {
        return elevator;
    }
}

# Remaining Elevator LLD Implementation Gaps (Flagged by Gemini 3.8-Flash)

This document tracks the remaining open gaps and known limitations in the elevator simulation module.

---

## 1. Stop Scheduling Does Not Defer Calls Behind the Elevator

* **Description**:
  The controller manages movement using only two active queues: `upStops` (min-heap) and `downStops` (max-heap). When an elevator is moving in one direction and receives a request in the *same* direction but **behind** its current position, adding it directly into the active queue places an already-passed floor at the top of the heap.

* **Concrete Failure Scenario**:
  1. Elevator is at **Floor 3 moving UP** towards **Floor 10** (`upStops = [Floor 10]`).
  2. Request A arrives: Passenger at Floor 1 requests DOWN (`downStops = [Floor 1]`).
  3. Request B arrives: Passenger at Floor 2 requests UP.
  4. Floor 2 is offered into `upStops`, making `upStops = [Floor 2, Floor 10]`.
  5. In `processUpQueue()`, `upStops.peek()` now returns `Floor 2`.
  6. The car checks `currentFloor (3) < nextFloor (2)` (**false**) and `currentFloor == nextFloor` (**false**).
  7. **Result**: The car stalls at Floor 3 and cannot advance upward to Floor 10.

* **Required Resolution**:
  Implement 4-queue partitioning or secondary deferred queues:
  - `currentUpStops`: Floors $\ge \text{currentFloor}$ to visit on the current upward sweep.
  - `deferredUpStops`: Floors $< \text{currentFloor}$ requesting UP, held until the next upward cycle.
  - `currentDownStops`: Floors $\le \text{currentFloor}$ to visit on the current downward sweep.
  - `deferredDownStops`: Floors $> \text{currentFloor}$ requesting DOWN, held until the next downward cycle.
  - When the elevator reaches a turnaround floor, merge the corresponding deferred queue into the active queue.

---

## 2. Out-of-Service Status Is Not Enforced in Simulation Stepping

* **Description**:
  While the dispatch strategy ([`EstimatedTimeOfArrivalStrategy`](src/main/java/com/lld/elevator/strategy/EstimatedTimeOfArrivalStrategy.java)) filters out `OUT_OF_SERVICE` cars and returns `null` when all cars are unavailable, the simulation loop in [`ElevatorSystem.stepSimulation()`](src/main/java/com/lld/elevator/service/ElevatorSystem.java) steps controllers as long as `controller.hasPendingRequests() || controller.getElevator().getState() == ElevatorState.MOVING`, without checking if `car.getState() != ElevatorState.OUT_OF_SERVICE`.
  Similarly, [`ElevatorController.step()`](src/main/java/com/lld/elevator/model/ElevatorController.java) lacks a guard against stepping an out-of-service car.

* **Required Resolution**:
  - Add active checks in `stepSimulation()` and `controller.step()` to halt movement and stop processing for cars in `ElevatorState.OUT_OF_SERVICE`.
  - Provide a clean maintenance and recovery API to take cars offline and redistribute their pending requests to available cars.

---

## 3. Mutable State Exposure via Public Car Setters

* **Description**:
  While `ElevatorController.getUpStops()` and `getDownStops()` return defensive copies to protect queue integrity, the mutators in [`ElevatorCar`](src/main/java/com/lld/elevator/model/ElevatorCar.java) (`setCurrentFloor`, `setCurrentDirection`, `setState`, `setDoorState`) are currently `public`. This allows external classes to directly alter a car's physical position or state, bypassing the controller's synchronization and invariant checks.

* **Required Resolution**:
  - Scope `ElevatorCar` mutators to package-private so that only [`ElevatorController`](src/main/java/com/lld/elevator/model/ElevatorController.java) within `com.lld.elevator.model` can mutate car state.

# Remaining Elevator LLD Implementation Gaps

This document tracks the remaining open gaps and known limitations in the elevator simulation module.

---

## 1. Out-of-Service Status Is Not Enforced in Simulation Stepping

* **Description**:
  While the dispatch strategy ([`EstimatedTimeOfArrivalStrategy`](../src/main/java/com/lld/elevator/strategy/EstimatedTimeOfArrivalStrategy.java)) filters out `OUT_OF_SERVICE` cars and returns `null` when all cars are unavailable, the simulation loop in [`ElevatorSystem.stepSimulation()`](../src/main/java/com/lld/elevator/service/ElevatorSystem.java) steps controllers as long as `controller.hasPendingRequests() || controller.getElevator().getState() == ElevatorState.MOVING`, without checking if `car.getState() != ElevatorState.OUT_OF_SERVICE`.
  Similarly, [`ElevatorController.step()`](../src/main/java/com/lld/elevator/model/ElevatorController.java) lacks a guard against stepping an out-of-service car.

* **Required Resolution**:
  - Add active checks in `stepSimulation()` and `controller.step()` to halt movement and stop processing for cars in `ElevatorState.OUT_OF_SERVICE`.
  - Provide a clean maintenance and recovery API to take cars offline and redistribute their pending requests to available cars.

---

## 2. Mutable State Exposure via Public Car Setters

* **Description**:
  While `ElevatorController.getUpStops()` and `getDownStops()` return defensive copies to protect queue integrity, the mutators in [`ElevatorCar`](../src/main/java/com/lld/elevator/model/ElevatorCar.java) (`setCurrentFloor`, `setCurrentDirection`, `setState`, `setDoorState`) are currently `public`. This allows external classes to directly alter a car's physical position or state, bypassing the controller's synchronization and invariant checks.

* **Required Resolution**:
  - Scope `ElevatorCar` mutators to package-private so that only [`ElevatorController`](../src/main/java/com/lld/elevator/model/ElevatorController.java) within `com.lld.elevator.model` can mutate car state.

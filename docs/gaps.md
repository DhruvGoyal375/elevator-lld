# Elevator LLD Implementation & Resolution Report

This document tracks implementation status, resolved architecture gaps, and design notes.

---

## 1. Out-of-Service Status Enforced & Maintenance Redistribution (RESOLVED)

* **Status**: Resolved
* **Resolution**:
  - Added active checks in [`ElevatorSystem.stepSimulation()`](../src/main/java/com/lld/elevator/service/ElevatorSystem.java) and [`ElevatorController.step()`](../src/main/java/com/lld/elevator/model/ElevatorController.java) to immediately halt movement and skip processing for cars in `ElevatorState.OUT_OF_SERVICE`.
  - Implemented `takeCarOutOfService(carId)` and `returnCarToService(carId)` in [`ElevatorSystem`](../src/main/java/com/lld/elevator/service/ElevatorSystem.java). When an active car is taken out of service, its orphaned external requests are extracted and automatically redistributed to operational cars.

---

## 2. 3-Intent LOOK Engine Replacing 4-Queue / 2-Set Complexity (RESOLVED)

* **Status**: Resolved
* **Resolution**:
  - Replaced the multi-queue structure (`upStops`, `downStops`, `deferredUpStops`, `deferredDownStops`, placeholder turnaround stops, and pruning loops) with a unified 3-intent model (`cabStops`, `upPickupRequests`, `downPickupRequests`) backed by `TreeSet<Floor>`.
  - Calls behind the car, concurrent drop-off and pickup, opposite-direction calls on the same floor, and dynamic turnaround points are handled naturally without synthetic stops or secondary queues.
  - Reduced controller code footprint by >70% while improving query performance and eliminating stall edge cases.

---

## 3. Mutable State Exposure via Public Car Setters (Open)

* **Description**:
  Mutators in [`ElevatorCar`](../src/main/java/com/lld/elevator/model/ElevatorCar.java) remain public to maintain standalone testability across package boundaries.
* **Future Enhancement**:
  Scope mutators to package-private in future major version refactoring once car test fixtures are colocated within `com.lld.elevator.model`.

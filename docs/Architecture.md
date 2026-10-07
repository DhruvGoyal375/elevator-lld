## 1. Complete UML Class Diagram

```mermaid
classDiagram
    direction TB
%% Enums
    class Direction {
        <<enumeration>>
        UP
        DOWN
        IDLE
    }

    class ElevatorState {
        <<enumeration>>
        MOVING
        STOPPED
        OUT_OF_SERVICE
    }

    class DoorState {
        <<enumeration>>
        OPEN
        CLOSED
    }

%% Interfaces
    class CarStatusListener {
        <<interface>>
        +onCarStatusUpdate(int carId, Floor currentFloor, Direction direction, ElevatorState state, int pendingStops) void
    }

    class ElevatorSelectionStrategy {
        <<interface>>
        +selectElevator(List~ElevatorController~ elevatorControllers, ExternalRequest request) ElevatorController
    }

    class Comparable~Floor~ {
        <<interface>>
        +compareTo(Floor other) int
    }

%% Models and Flyweight
    class Floor {
        -Map~Integer, Floor~ CACHE$
        -int id
        -Floor(int id)
        +of(int id)$ Floor
        +getId() int
        +compareTo(Floor other) int
        +equals(Object o) boolean
        +hashCode() int
        +toString() String
    }

    class InternalRequest {
        -Floor destinationFloor
        +InternalRequest(Floor destinationFloor)
        +getDestinationFloor() Floor
        +toString() String
    }

    class ExternalRequest {
        -Direction direction
        -Floor sourceFloor
        +ExternalRequest(Direction direction, Floor sourceFloor)
        +getSourceFloor() Floor
        +getDirection() Direction
        +toString() String
    }

    class ElevatorCar {
        -int id
        -Floor currentFloor
        -Direction currentDirection
        -ElevatorState state
        -DoorState doorState
        -int capacity
        +ElevatorCar(int id, Floor initialFloor, int capacity)
        +openDoors() void
        +closeDoors() void
        +getId() int
        +getCurrentFloor() Floor
        +setCurrentFloor(Floor currentFloor) void
        +getCurrentDirection() Direction
        +setCurrentDirection(Direction currentDirection) void
        +getState() ElevatorState
        +setState(ElevatorState state) void
        +getDoorState() DoorState
        +setDoorState(DoorState doorState) void
        +getCapacity() int
    }

%% Controller
    class ElevatorController {
        -ElevatorCar elevator
        -TreeSet~Floor~ cabStops
        -TreeSet~Floor~ upPickupRequests
        -TreeSet~Floor~ downPickupRequests
        -List~CarStatusListener~ listeners
        +ElevatorController(ElevatorCar elevator)
        +registerListener(CarStatusListener listener) void
        +notifyListeners() void
        +handleInternalRequest(InternalRequest internalRequest) void
        +assignExternalPickup(ExternalRequest externalRequest) void
        +step() void
        -processUpStep() void
        -processDownStep() void
        -shouldStopAtFloorMovingUp(Floor nextFloor) boolean
        -shouldStopAtFloorMovingDown(Floor nextFloor) boolean
        -handleArrivalAtFloor(Floor floor, Direction travelDir) void
        -transitionFromUpSweep() void
        -transitionFromDownSweep() void
        -pickInitialDirectionIfIdle() void
        -hasRequestsAbove(Floor f) boolean
        -hasRequestsBelow(Floor f) boolean
        -getClosestRequestAbove(Floor f) Floor
        -getClosestRequestBelow(Floor f) Floor
        +clearAndGetPendingExternalRequests() List~ExternalRequest~
        +hasPendingRequests() boolean
        +getPendingStopsCount() int
        +getHighestPendingFloor() int
        +getLowestPendingFloor() int
        +countStopsStrictlyBetween(int low, int high, Direction dir) int
        +takeOutOfService() void
        +returnToService() void
        +getUpPickupRequests() Set~Floor~
        +getDownPickupRequests() Set~Floor~
        +getInternalDestinations() Set~Floor~
        +getElevator() ElevatorCar
    }

%% System Service
    class ElevatorSystem {
        -int minFloor
        -int maxFloor
        -List~ElevatorController~ controllers
        -ElevatorSelectionStrategy strategy
        +ElevatorSystem(int minFloor, int maxFloor, List~ElevatorController~ controllers, ElevatorSelectionStrategy strategy)
        +ElevatorSystem(List~ElevatorController~ controllers, ElevatorSelectionStrategy strategy)
        +validateFloor(Floor floor) void
        +getMinFloor() int
        +getMaxFloor() int
        +getControllers() List~ElevatorController~
        +handleExternalRequest(ExternalRequest request) void
        +onCarStatusUpdate(int carId, Floor currentFloor, Direction direction, ElevatorState state, int pendingStops) void
        +stepSimulation() void
        +hasActiveRequests() boolean
        +takeCarOutOfService(int carId) void
        +returnCarToService(int carId) void
    }

%% Strategy Implementation
    class EstimatedTimeOfArrivalStrategy {
        -int SECONDS_PER_FLOOR$
        -int STOP_DWELL_SECONDS$
        +selectElevator(List~ElevatorController~ controllers, ExternalRequest request) ElevatorController
        -computeEta(ElevatorCar car, ElevatorController controller, ExternalRequest request) int
    }

%% Panels
    class CarOperatingPanel {
        -ElevatorController localController
        +CarOperatingPanel(ElevatorController localController)
        +getLocalController() ElevatorController
        +pressFloorButton(Floor destinationFloor) void
    }

    class HallwayPanel {
        -Floor operatingFloor
        -ElevatorSystem centralSystem
        +HallwayPanel(Floor operatingFloor, ElevatorSystem centralSystem)
        +getOperatingFloor() Floor
        +getCentralSystem() ElevatorSystem
        +pressUp() void
        +pressDown() void
    }

%% Relationships
    Comparable~Floor~ <|.. Floor
    ElevatorSelectionStrategy <|.. EstimatedTimeOfArrivalStrategy
    CarStatusListener <|.. ElevatorSystem

ElevatorSystem o-- "1..*" ElevatorController: coordinates
ElevatorSystem --> "1" ElevatorSelectionStrategy: uses
ElevatorSystem ..> ExternalRequest: dispatches

ElevatorController *-- "1" ElevatorCar: controls
ElevatorController o-- "0..*" CarStatusListener: notifies
ElevatorController ..> InternalRequest : receives
ElevatorController ..> ExternalRequest: receives
ElevatorController --> "0..*" Floor: tracks (TreeSet)

ElevatorCar --> "1" Floor: currentFloor
ElevatorCar --> "1" Direction: currentDirection
ElevatorCar --> "1" ElevatorState: state
ElevatorCar --> "1" DoorState: doorState

InternalRequest --> "1" Floor : destinationFloor
ExternalRequest --> "1" Floor : sourceFloor
ExternalRequest --> "1" Direction : direction

CarOperatingPanel --> "1" ElevatorController: binds to
CarOperatingPanel ..> InternalRequest: creates

HallwayPanel --> "1" ElevatorSystem : communicates with
HallwayPanel --> "1" Floor: located at
HallwayPanel ..> ExternalRequest: creates
```

---

## 2. Interaction Sequence Diagrams

### A. External Hallway Call Workflow

```mermaid
sequenceDiagram
    autonumber
    actor Passenger
    participant HP as HallwayPanel (Floor 4)
    participant ES as ElevatorSystem
    participant Strategy as EstimatedTimeOfArrivalStrategy
    participant EC as ElevatorController 1
    participant Car as ElevatorCar 1
    Passenger ->> HP: pressDown()
    HP ->> ES: handleExternalRequest(ExternalRequest(DOWN, Floor 4))
    ES ->> ES: validateFloor(Floor 4)
    ES ->> Strategy: selectElevator(controllers, request)
    Strategy ->> EC: inspect status & computeEta()
    Strategy -->> ES: returns bestController (EC 1)
    ES ->> EC: assignExternalPickup(request)
    EC ->> EC: downPickupRequests.add(Floor 4)
    EC ->> Car: setCurrentDirection(UP) (if idle and below Floor 4)
    EC ->> ES: notifyListeners() (onCarStatusUpdate)
```

### B. Internal Cab Request Workflow

```mermaid
sequenceDiagram
    autonumber
    actor Passenger
    participant COP as CarOperatingPanel (Car 1)
    participant EC as ElevatorController 1
    participant Car as ElevatorCar 1
    participant ES as ElevatorSystem
    Passenger ->> COP: pressFloorButton(Floor 10)
    COP ->> EC: handleInternalRequest(InternalRequest(Floor 10))
    EC ->> EC: cabStops.add(Floor 10)
    alt If car was IDLE
        EC ->> Car: setCurrentDirection(UP)
    end
    EC ->> ES: notifyListeners() (onCarStatusUpdate)
```

### C. Simulation Step & Floor Arrival Workflow (LOOK Algorithm)

```mermaid
sequenceDiagram
    autonumber
    participant ES as ElevatorSystem
    participant EC as ElevatorController 1
    participant Car as ElevatorCar 1
    ES ->> EC: step()
    alt Moving UP and has requests above
        EC ->> Car: setState(MOVING)
        EC ->> Car: setCurrentFloor(Floor n+1)
        EC ->> ES: notifyListeners()
        alt Should stop at Floor n+1
            EC ->> Car: setState(STOPPED)
            EC ->> Car: openDoors()
            EC ->> EC: remove fulfilled drop-offs / pickups
            EC ->> Car: closeDoors()
            EC ->> EC: evaluate next direction (continue or turnaround)
            EC ->> ES: notifyListeners()
        end
    else No requests above
        EC ->> EC: transitionFromUpSweep() (turn DOWN or IDLE)
        EC ->> ES: notifyListeners()
    end
```

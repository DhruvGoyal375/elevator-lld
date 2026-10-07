package com.lld.elevator;

import com.lld.elevator.enums.Direction;
import com.lld.elevator.model.ElevatorCar;
import com.lld.elevator.model.ElevatorController;
import com.lld.elevator.model.ExternalRequest;
import com.lld.elevator.model.Floor;
import com.lld.elevator.panel.CarOperatingPanel;
import com.lld.elevator.panel.HallwayPanel;
import com.lld.elevator.service.ElevatorSystem;
import com.lld.elevator.strategy.ElevatorSelectionStrategy;
import com.lld.elevator.strategy.EstimatedTimeOfArrivalStrategy;
import java.util.List;

public class ElevatorSystemDemo {
    public static void main(String[] args) {
        System.out.println("=======================================================================");
        System.out.println(" SCENARIO 1: Internal Destinations vs External Pickups (Multi-Car)    ");
        System.out.println("=======================================================================");
        runInternalDestinationsScenario();

        System.out.println("\n=======================================================================");
        System.out.println(" SCENARIO 2: Significance of upPickupRequests & downPickupRequests    ");
        System.out.println("             (Opposite-Direction Hallway Calls at Same Floor)         ");
        System.out.println("=======================================================================");
        runPickupRequestsSignificanceScenario();

        System.out.println("\n=======================================================================");
        System.out.println(" SCENARIO 3: Dynamic Turnaround Placeholder Pruning (Single Car)      ");
        System.out.println("=======================================================================");
        runDynamicTurnaroundPruningScenario();

        System.out.println("\n=======================================================================");
        System.out.println(" SCENARIO 4: Gap 1 Fix: 4-Queue Partitioning for Calls Behind Car    ");
        System.out.println("=======================================================================");
        runDeferredCallsBehindCarScenario();
    }

    /**
     * SCENARIO 1:
     * Demonstrates the distinct role of internalDestinations vs external up/down pickups.
     * When Car 1 reaches Floor 4 with both a cab passenger wanting to exit and a hallway
     * passenger wanting to board in the same direction, both intents are serviced concurrently
     * during a single door-open cycle without redundant stops.
     */
    private static void runInternalDestinationsScenario() {
        Floor ground = Floor.of(0);
        Floor floor2 = Floor.of(2);
        Floor floor4 = Floor.of(4);
        Floor floor5 = Floor.of(5);

        ElevatorCar car1 = new ElevatorCar(1, ground, 8);
        ElevatorCar car2 = new ElevatorCar(2, ground, 8);

        ElevatorController controller1 = new ElevatorController(car1);
        ElevatorController controller2 = new ElevatorController(car2);

        List<ElevatorController> controllers = List.of(controller1, controller2);
        ElevatorSelectionStrategy strategy = new EstimatedTimeOfArrivalStrategy();
        ElevatorSystem centralSystem = new ElevatorSystem(controllers, strategy);

        CarOperatingPanel copCar1 = new CarOperatingPanel(controller1);

        HallwayPanel hallwayFloor2 = new HallwayPanel(floor2, centralSystem);
        HallwayPanel hallwayFloor5 = new HallwayPanel(floor5, centralSystem);

        // 1. Passenger at Floor 2 calls for UP
        hallwayFloor2.pressUp();

        for (int i = 1; i <= 2; i++) {
            System.out.printf("%n[Cycle %d]%n", i);
            centralSystem.stepSimulation();
        }

        // 2. Demonstrating the significance of internalDestinations:
        // - Passenger inside Car 1 presses Floor 4 (tracked in internalDestinations).
        // - In the hallway at Floor 4, another passenger calls UP (assigned to Car 1 as upPickupRequests).
        // When Car 1 arrives at Floor 4, it fulfills BOTH intents concurrently:
        // internal drop-off (passenger exiting) + external UP pickup (passenger boarding).
        System.out.println("\n--- Demonstrating internalDestinations vs Hallway Pickups ---");
        copCar1.pressFloorButton(floor4);
        controller1.assignExternalPickup(new ExternalRequest(Direction.UP, floor4));

        // 3. Passenger at Floor 5 calls DOWN on hallway panel (dispatched to Car 2)
        hallwayFloor5.pressDown();

        int cycle = 3;
        while (centralSystem.hasActiveRequests()) {
            System.out.printf("%n[Cycle %d]%n", cycle++);
            centralSystem.stepSimulation();
        }

        System.out.println("\nScenario 1 completed. Elevators are IDLE.");
    }

    /**
     * SCENARIO 2:
     * Demonstrates the significance of upPickupRequests and downPickupRequests.
     * When passengers on the SAME floor (Floor 3) press BOTH UP and DOWN:
     * - The car travels UP to Floor 7.
     * - When stopping at Floor 3 moving UP, it services ONLY the UP pickup.
     * - The DOWN passenger remains queued in downPickupRequests and is serviced
     * on the subsequent downward trip, preventing request collapsing or wrong-direction boarding.
     */
    private static void runPickupRequestsSignificanceScenario() {
        Floor ground = Floor.of(0);
        Floor floor3 = Floor.of(3);
        Floor floor7 = Floor.of(7);

        ElevatorCar car = new ElevatorCar(1, ground, 8);
        ElevatorController controller = new ElevatorController(car);
        ElevatorSystem system = new ElevatorSystem(0, 10, List.of(controller), new EstimatedTimeOfArrivalStrategy());

        HallwayPanel hallwayFloor3 = new HallwayPanel(floor3, system);
        CarOperatingPanel copCar = new CarOperatingPanel(controller);

        System.out.println("\n[Setup] Passenger inside Car 1 requests Floor 7 (internal cab destination).");
        copCar.pressFloorButton(floor7);

        System.out.println("[Setup] Two hallway passengers at Floor 3 press BOTH UP and DOWN buttons.");
        hallwayFloor3.pressUp();
        hallwayFloor3.pressDown();

        System.out.println("\n[Simulation] Stepping elevator upward towards Floor 7...");
        int cycle = 1;
        while (system.hasActiveRequests()) {
            System.out.printf("%n[Cycle %d]%n", cycle++);
            system.stepSimulation();
        }

        System.out.println(
                "\nScenario 2 completed. UP and DOWN calls on Floor 3 were serviced in their respective directions without collapsing.");
    }

    /**
     * SCENARIO 3:
     * Demonstrates dynamic turnaround placeholder pruning:
     * - Car 1 is IDLE at Floor 0.
     * - Passenger calls DOWN at Floor 4 (Car 1 targets Floor 4 as turnaround).
     * - While Car 1 is moving UP (at Floor 2), another passenger calls DOWN at Floor 6.
     * - Floor 6 becomes the true peak turnaround floor. Floor 4 is pruned from upStops so
     * the car passes Floor 4 express on the way up, turns around at Floor 6, and services
     * Floor 4 on the downward pass (preventing ghost stops).
     */
    private static void runDynamicTurnaroundPruningScenario() {
        Floor ground = Floor.of(0);
        Floor floor4 = Floor.of(4);
        Floor floor6 = Floor.of(6);

        ElevatorCar car = new ElevatorCar(1, ground, 8);
        ElevatorController controller = new ElevatorController(car);
        ElevatorSelectionStrategy strategy = new EstimatedTimeOfArrivalStrategy();
        ElevatorSystem system = new ElevatorSystem(0, 10, List.of(controller), strategy);

        HallwayPanel hallwayFloor4 = new HallwayPanel(floor4, system);
        HallwayPanel hallwayFloor6 = new HallwayPanel(floor6, system);

        System.out.println("\n[Initial State] Car 1 is IDLE at Floor 0.");
        System.out.println("Step 1: Passenger at Floor 4 requests DOWN on hallway panel.");
        hallwayFloor4.pressDown();

        System.out.println("\n[Simulation] Stepping elevator upward towards Floor 4...");
        for (int i = 1; i <= 2; i++) {
            System.out.printf("%n[Step Cycle %d]%n", i);
            system.stepSimulation();
        }

        System.out.printf(
                "%n[Event] While Car 1 is at Floor %d moving UP, passenger at Floor 6 requests DOWN!%n",
                car.getCurrentFloor().getId());
        System.out.println(
                ">> Floor 6 is now the peak turnaround. Floor 4 turnaround placeholder is dynamically PRUNED from upStops.");
        hallwayFloor6.pressDown();

        int cycle = 3;
        while (system.hasActiveRequests()) {
            System.out.printf("%n[Step Cycle %d]%n", cycle++);
            system.stepSimulation();
        }

        System.out.println(
                "\nScenario 3 completed. Car 1 bypassed Floor 4 express going UP, reached peak Floor 6, and serviced Floor 4 on the way DOWN.");
    }

    /**
     * SCENARIO 4:
     * Demonstrates Gap 1 Fix (4-Queue Partitioning for Calls Behind the Elevator):
     * - Car 1 is at Floor 3 moving UP towards Floor 10.
     * - Passenger at Floor 1 requests DOWN.
     * - Passenger at Floor 2 requests UP (behind moving car!).
     * - Floor 2 is held in deferredUpStops, so upStops min-heap is not corrupted.
     * - Car moves UP to Floor 10 without stalling.
     * - Car sweeps DOWN to Floor 1, servicing Floor 1 DOWN.
     * - Car turns around and climbs to Floor 2, servicing the deferred UP call.
     */
    private static void runDeferredCallsBehindCarScenario() {
        Floor floor1 = Floor.of(1);
        Floor floor2 = Floor.of(2);
        Floor floor3 = Floor.of(3);
        Floor floor10 = Floor.of(10);

        ElevatorCar car = new ElevatorCar(1, floor3, 10);
        ElevatorController controller = new ElevatorController(car);
        ElevatorSystem system = new ElevatorSystem(0, 10, List.of(controller), new EstimatedTimeOfArrivalStrategy());

        CarOperatingPanel copCar = new CarOperatingPanel(controller);
        HallwayPanel hallwayFloor1 = new HallwayPanel(floor1, system);
        HallwayPanel hallwayFloor2 = new HallwayPanel(floor2, system);

        System.out.println("\n[Initial State] Car 1 is at Floor 3. Passenger inside cab presses Floor 10.");
        copCar.pressFloorButton(floor10);

        System.out.println("Step 1: Hallway passenger at Floor 1 requests DOWN.");
        hallwayFloor1.pressDown();

        System.out.println("Step 2: Hallway passenger at Floor 2 requests UP (behind car moving UP!).");
        hallwayFloor2.pressUp();
        System.out.printf(
                ">> Request telemetry: internal: %s, up pickups: %s, down pickups: %s%n",
                controller.getInternalDestinations(),
                controller.getUpPickupRequests(),
                controller.getDownPickupRequests());

        System.out.println("\n[Simulation] Stepping simulation through full cycle...");
        int cycle = 1;
        while (system.hasActiveRequests()) {
            System.out.printf("%n[Cycle %d]%n", cycle++);
            system.stepSimulation();
        }

        System.out.println(
                "\nScenario 4 completed. Car 1 reached Floor 10 without stalling, serviced Floor 1 DOWN, and then picked up deferred Floor 2 UP.");
    }
}

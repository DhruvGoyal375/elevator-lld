package com.lld.elevator;

import com.lld.elevator.model.ElevatorCar;
import com.lld.elevator.model.ElevatorController;
import com.lld.elevator.model.Floor;
import com.lld.elevator.panel.CarOperatingPanel;
import com.lld.elevator.panel.HallwayPanel;
import com.lld.elevator.service.ElevatorSystem;
import com.lld.elevator.strategy.ElevatorSelectionStrategy;
import com.lld.elevator.strategy.EstimatedTimeOfArrivalStrategy;
import java.util.List;

public class ElevatorSystemDemo {
    public static void main(String[] args) {
        Floor ground = Floor.of(0);
        Floor floor1 = Floor.of(1);
        Floor floor2 = Floor.of(2);
        Floor floor3 = Floor.of(3);
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
        CarOperatingPanel copCar2 = new CarOperatingPanel(controller2);

        HallwayPanel hallwayFloor2 = new HallwayPanel(floor2, centralSystem);
        HallwayPanel hallwayFloor5 = new HallwayPanel(floor5, centralSystem);

        // Passenger at Floor 2 calls for UP
        hallwayFloor2.pressUp();

        for (int i = 1; i <= 2; i++) {
            System.out.printf("%n[Cycle %d]%n", i);
            centralSystem.stepSimulation();
        }

        // Passenger inside Car 1 presses Floor 4 directly on cabin panel
        copCar1.pressFloorButton(floor4);

        // Another passenger outside at Floor 5 presses DOWN on hallway panel
        hallwayFloor5.pressDown();

        int cycle = 3;
        while (centralSystem.hasActiveRequests()) {
            System.out.printf("%n[Cycle %d]%n", cycle++);
            centralSystem.stepSimulation();
        }

        System.out.println("\nAll requests completed. Elevators are IDLE.");
    }
}

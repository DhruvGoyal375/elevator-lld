package com.lld.elevator.panel;

import com.lld.elevator.model.ElevatorController;
import com.lld.elevator.model.Floor;
import com.lld.elevator.model.InternalRequest;
import java.util.Objects;

public class CarOperatingPanel {
    private final ElevatorController localController;

    public CarOperatingPanel(ElevatorController localController) {
        this.localController = Objects.requireNonNull(localController, "localController cannot be null");
    }

    public ElevatorController getLocalController() {
        return localController;
    }

    public void pressFloorButton(Floor destinationFloor) {
        System.out.printf(
                "[CarOperatingPanel %d] Button pressed for %s%n",
                localController.getElevator().getId(), destinationFloor);
        localController.handleInternalRequest(new InternalRequest(destinationFloor));
    }
}

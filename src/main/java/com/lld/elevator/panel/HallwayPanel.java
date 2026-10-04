package com.lld.elevator.panel;

import com.lld.elevator.enums.Direction;
import com.lld.elevator.model.ExternalRequest;
import com.lld.elevator.model.Floor;
import com.lld.elevator.service.ElevatorSystem;
import java.util.Objects;

public class HallwayPanel {
    private final Floor operatingFloor;
    private final ElevatorSystem centralSystem;

    public HallwayPanel(Floor operatingFloor, ElevatorSystem centralSystem) {
        this.operatingFloor = Objects.requireNonNull(operatingFloor, "operatingFloor cannot be null");
        this.centralSystem = Objects.requireNonNull(centralSystem, "centralSystem cannot be null");
        centralSystem.validateFloor(operatingFloor);
    }

    public Floor getOperatingFloor() {
        return operatingFloor;
    }

    public ElevatorSystem getCentralSystem() {
        return centralSystem;
    }

    public void pressUp() {
        System.out.printf("[HallwayPanel %s] UP button pressed%n", operatingFloor);
        centralSystem.handleExternalRequest(new ExternalRequest(Direction.UP, operatingFloor));
    }

    public void pressDown() {
        System.out.printf("[HallwayPanel %s] DOWN button pressed%n", operatingFloor);
        centralSystem.handleExternalRequest(new ExternalRequest(Direction.DOWN, operatingFloor));
    }
}

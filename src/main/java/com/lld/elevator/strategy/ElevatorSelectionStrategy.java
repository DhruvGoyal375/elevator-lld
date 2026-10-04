package com.lld.elevator.strategy;

import com.lld.elevator.model.ElevatorController;
import com.lld.elevator.model.ExternalRequest;
import java.util.List;

public interface ElevatorSelectionStrategy {
    public ElevatorController selectElevator(List<ElevatorController> elevatorControllers, ExternalRequest request);
}

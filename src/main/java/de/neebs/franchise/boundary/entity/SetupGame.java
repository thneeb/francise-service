package de.neebs.franchise.boundary.entity;

import de.neebs.franchise.client.entity.ComputerStrategy;
import de.neebs.franchise.client.entity.PlayerColor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SetupGame {
    private List<PlayerColor> availablePlayerColors;

    private List<String> selectedPlayerColors;

    private List<ComputerStrategy> availableAlgorithms;

    private List<String> selectedAlgorithms;
}

package de.neebs.franchise.boundary.frontend;

import de.neebs.franchise.client.entity.PlayerColor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SetupGame {
    private List<PlayerColor> availablePlayerColors;

    private List<String> selectedPlayerColors;
}

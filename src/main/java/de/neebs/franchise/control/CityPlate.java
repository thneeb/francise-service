package de.neebs.franchise.control;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@AllArgsConstructor
@EqualsAndHashCode
public class CityPlate {
    private boolean closed;
    private final List<PlayerColor> branches;
    private Integer extensionCosts;

    public static CityPlate copy(CityPlate plate) {
        return new CityPlate(plate.closed, new ArrayList<>(plate.branches), plate.extensionCosts);
    }
}

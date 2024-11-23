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

    public static CityPlate copy(CityPlate plate) {
        return new CityPlate(plate.isClosed(), new ArrayList<>(plate.getBranches()));
    }
}

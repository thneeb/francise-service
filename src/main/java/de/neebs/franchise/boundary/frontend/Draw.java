package de.neebs.franchise.boundary.frontend;

import de.neebs.franchise.client.entity.BonusTileUsage;
import de.neebs.franchise.client.entity.City;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class Draw {
    private BonusTileUsage bonusTileUsage;

    private List<City> extensions = new ArrayList<>();

    private Map<City, Integer> increases = new EnumMap<>(City.class);
}

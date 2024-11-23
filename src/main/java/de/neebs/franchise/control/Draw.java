package de.neebs.franchise.control;

import lombok.*;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@EqualsAndHashCode
@ToString
public class Draw {
    private Set<City> extension;
    private List<City> increase;
    private BonusTileUsage bonusTileUsage;

    public boolean isBonusTile() {
        return bonusTileUsage != null;
    }
}

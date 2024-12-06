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

    public boolean includes(Draw draw) {
        boolean result = true;
        if (extension != null && draw.getExtension() != null && extension.stream().noneMatch(f -> draw.getExtension().contains(f))) {
            result = false;
        }
        if (increase != null && draw.getIncrease() != null && increase.stream().noneMatch(f -> draw.getIncrease().contains(f))) {
            result = false;
        }
        return result;
    }

    public boolean isNull() {
        return (extension == null || extension.isEmpty()) && (increase == null || increase.isEmpty()) && bonusTileUsage == null;
    }

    public boolean isMoney() {
        return bonusTileUsage == BonusTileUsage.MONEY;
    }
}

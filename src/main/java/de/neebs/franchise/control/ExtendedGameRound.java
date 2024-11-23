package de.neebs.franchise.control;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExtendedGameRound {
    private final GameRound gameRound;
    private final AdditionalInfo additionalInfo;
}

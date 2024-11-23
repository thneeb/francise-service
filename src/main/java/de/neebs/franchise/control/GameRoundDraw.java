package de.neebs.franchise.control;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@EqualsAndHashCode
public class GameRoundDraw {
    private GameRoundDraw predecessor;
    private ExtendedGameRound gameRound;
    private Draw draw;
}

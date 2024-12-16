package de.neebs.franchise.control;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
public class GameRoundDraw {
    private Draw draw;
    private GameRound gameRound;
}

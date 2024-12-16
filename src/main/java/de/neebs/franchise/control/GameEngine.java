package de.neebs.franchise.control;

import java.util.List;

public interface GameEngine {
    List<GameRoundDraw> play(GameRound round, List<ComputerPlayer> players);

    ExtendedGameRound makeDraw(GameRound round, Draw draw);
}

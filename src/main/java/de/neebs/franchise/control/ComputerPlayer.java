package de.neebs.franchise.control;

import java.util.Map;

public interface ComputerPlayer {
    PlayerColor getPlayerColor();

    Draw evaluateDraw(GameRound round);
}

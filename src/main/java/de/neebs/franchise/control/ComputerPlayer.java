package de.neebs.franchise.control;

public interface ComputerPlayer {
    PlayerColor getPlayerColor();

    Draw evaluateDraw(GameRound round);
}

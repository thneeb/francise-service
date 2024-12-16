package de.neebs.franchise.control;

import java.util.Map;

public interface ComputerPlayer {
    Draw evaluateDraw(GameRound round, Map<String, Object> params);
}

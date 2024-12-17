package de.neebs.franchise.control;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.Map;

abstract class AbstractComputerPlayer implements ComputerPlayer{
    @Getter
    private final PlayerColor playerColor;
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, Object> params;

    AbstractComputerPlayer(PlayerColor playerColor, Map<String, Object> params) {
        this.playerColor = playerColor;
        this.params = params;
    }
}

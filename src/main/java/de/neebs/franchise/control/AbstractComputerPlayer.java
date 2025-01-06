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

    protected boolean getBoolean(Map<String, Object> map, String name, boolean defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object o = map.get(name);
        if (o instanceof Boolean value) {
            return value;
        } else {
            return defaultValue;
        }
    }

    protected int getInt(Map<String, Object> map, String name, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object o = map.get(name);
        if (o instanceof Integer value) {
            return value;
        } else if (o instanceof Long value) {
            return value.intValue();
        } else {
            return defaultValue;
        }
    }

    protected float getFloat(Map<String, Object> map, String name, float defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object o = map.get(name);
        if (o instanceof Float value) {
            return value;
        } else if (o instanceof Double value) {
            return value.floatValue();
        } else {
            return defaultValue;
        }
    }
}

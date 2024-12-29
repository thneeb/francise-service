package de.neebs.franchise.control;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.Map;

abstract class AbstractLearningModel implements LearningModel {
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, Object> params;

    AbstractLearningModel(Map<String, Object> params) {
        this.params = params;
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

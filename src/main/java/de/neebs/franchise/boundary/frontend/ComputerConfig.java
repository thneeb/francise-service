package de.neebs.franchise.boundary.frontend;

import de.neebs.franchise.client.entity.ComputerStrategy;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ComputerConfig {
    private ComputerStrategy strategy;
    private Map<String, Object> parameters;
}

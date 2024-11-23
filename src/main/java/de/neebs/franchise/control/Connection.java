package de.neebs.franchise.control;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@EqualsAndHashCode(of = "cities")
@RequiredArgsConstructor
class Connection {
    private final Set<City> cities;
    private final int costs;
}

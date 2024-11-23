package de.neebs.franchise.control;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
class ConnectionTest {
    @Test
    void testConnectionFromCity() {
        Assertions.assertEquals(7, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SEATTLE)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SPOKANE)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.PORTLAND)).count());
        Assertions.assertEquals(4, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.BOISE)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.POCATELLO)).count());

        Assertions.assertEquals(7, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SAN_FRANCISCO)).count());
        Assertions.assertEquals(6, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.LOS_ANGELES)).count());
        Assertions.assertEquals(2, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.RENO)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.LAS_VEGAS)).count());

        Assertions.assertEquals(7, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.PHOENIX)).count());
        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.ALBUQUERQUE)).count());
        Assertions.assertEquals(6, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.DENVER)).count());
        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SALT_LAKE_CITY)).count());
        Assertions.assertEquals(4, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.FLAGSTAFF)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.PUEBLO)).count());

        Assertions.assertEquals(8, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CASPER)).count());
        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.BILLINGS)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CONRAD)).count());
        Assertions.assertEquals(4, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.FARGO)).count());
        Assertions.assertEquals(4, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SIOUX_FALLS)).count());

        Assertions.assertEquals(4, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.MINNEAPOLIS)).count());
        Assertions.assertEquals(9, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CHICAGO)).count());
        Assertions.assertEquals(4, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.DETROIT)).count());
        Assertions.assertEquals(4, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.INDIANAPOLIS)).count());

        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.PITTSBURGH)).count());
        Assertions.assertEquals(6, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.NEW_YORK)).count());

        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.WASHINGTON)).count());
        Assertions.assertEquals(6, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CHARLOTTE)).count());
        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CHARLESTON)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.RALEIGH)).count());

        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.ATLANTA)).count());
        Assertions.assertEquals(2, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.JACKSONVILLE)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.MONTGOMERY)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.HUNTSVILLE)).count());

        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.EL_PASO)).count());
        Assertions.assertEquals(7, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.OKLAHOMA_CITY)).count());
        Assertions.assertEquals(6, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.DALLAS)).count());
        Assertions.assertEquals(7, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.HOUSTON)).count());

        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.OGALLALA)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.DODGE_CITY)).count());
        Assertions.assertEquals(5, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.OMAHA)).count());
        Assertions.assertEquals(10, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.KANSAS_CITY)).count());
        Assertions.assertEquals(6, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.ST_LOUIS)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.LITTLE_ROCK)).count());
        Assertions.assertEquals(3, Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.MEMPHIS)).count());
    }

    @Test
    void testStreetValues() {
        // UPPER_WEST
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L, 3, 2L, 5, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SEATTLE)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 3, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.PORTLAND)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 2L, 1, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SPOKANE)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 2L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.BOISE)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 2L, 1, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.POCATELLO)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // MONTANA
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CONRAD)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 2L, 3, 3L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.BILLINGS)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L, 3, 1L, 5, 3L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CASPER)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 2L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.FARGO)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 3L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SIOUX_FALLS)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // GREAT_LAKES
        Assertions.assertEquals(Map.of(0, 1L, 3, 3L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.MINNEAPOLIS)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 2L, 3, 2L, 5, 2L, 8, 3L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CHICAGO)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 1L, 3, 1L, 5, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.DETROIT)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 3L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.INDIANAPOLIS)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // NEW_YORK
        Assertions.assertEquals(Map.of(1, 1L, 3, 2L, 5, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.PITTSBURGH)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(3, 1L, 5, 4L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.NEW_YORK)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // WASHINGTON
        Assertions.assertEquals(Map.of(1, 3L, 3, 1L, 5, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.WASHINGTON)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 3, 3L, 5, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CHARLOTTE)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 1L, 3, 1L, 5, 2L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.CHARLESTON)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.RALEIGH)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // FLORIDA
        Assertions.assertEquals(Map.of(0, 1L, 1, 1L, 5, 2L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.ATLANTA)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 1L, 3, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.JACKSONVILLE)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.MONTGOMERY)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 2L, 1, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.HUNTSVILLE)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // TEXAS
        Assertions.assertEquals(Map.of(1, 4L, 3, 1L, 5, 1L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.OKLAHOMA_CITY)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 3, 1L, 5, 4L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.DALLAS)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 1L, 3, 1L, 5, 3L, 8, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.HOUSTON)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 2L, 1, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.EL_PASO)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // GRAND CANYON
        Assertions.assertEquals(Map.of(1, 3L, 5, 2L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.DENVER)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 1L, 3, 1L, 5, 1L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.ALBUQUERQUE)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 1L, 3, 1L, 5, 3L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.PHOENIX)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.PUEBLO)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 3L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SALT_LAKE_CITY)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 2L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.FLAGSTAFF)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // CALIFORNIA
        Assertions.assertEquals(Map.of(1, 3L, 3, 1L, 5, 2L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.SAN_FRANCISCO)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 2L, 5, 2L, 8, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.LOS_ANGELES)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.RENO)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.LAS_VEGAS)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));

        // CENTRAL
        Assertions.assertEquals(Map.of(0, 1L, 1, 1L, 5, 2L, 8, 1L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.OMAHA)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 2L, 5, 5L, 8, 3L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.KANSAS_CITY)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 1L, 3, 1L, 5, 3L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.ST_LOUIS)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(1, 5L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.OGALLALA)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.DODGE_CITY)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.MEMPHIS)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
        Assertions.assertEquals(Map.of(0, 1L, 1, 2L),
                Rules.CONNECTIONS.stream().filter(f -> f.getCities().contains(City.LITTLE_ROCK)).collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting())));
    }

    @Test
    void testStreetCounts() {
        Map<Integer, Long> map = Rules.CONNECTIONS.stream().collect(Collectors.groupingBy(Connection::getCosts, Collectors.counting()));

        Assertions.assertEquals(11, map.get(8));
        Assertions.assertEquals(17, map.get(0));
        Assertions.assertEquals(24, map.get(5));

    }
}

package de.neebs.franchise.control;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class EvaluationTest {
    private final FranchiseCoreService franchiseCoreService = new FranchiseCoreService();

    @Test
    void openPlatesFull2PlayersTest() {
        FranchiseService service = new FranchiseService(franchiseCoreService);
        GameRound round = franchiseCoreService.init(List.of(PlayerColor.BLUE, PlayerColor.RED));
        int openPlates = service.openPlates(round);
        Assertions.assertEquals(94, openPlates);
    }

    @Test
    void openPlatesFull3PlayersTest() {
        FranchiseService service = new FranchiseService(franchiseCoreService);
        GameRound round = franchiseCoreService.init(List.of(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.BLACK));
        int openPlates = service.openPlates(round);
        Assertions.assertEquals(121, openPlates);
    }

    @Test
    void openPlatesFull3PlayersNewYorkClosedTest() {
        FranchiseService service = new FranchiseService(franchiseCoreService);
        GameRound round = franchiseCoreService.init(List.of(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.BLACK));
        round.getPlates().put(City.NEW_YORK, new CityPlate(true, List.of(PlayerColor.BLUE)));
        int openPlates = service.openPlates(round);
        Assertions.assertEquals(121 - City.NEW_YORK.getSize(), openPlates);
    }

    @Test
    void openPlatesFull3PlayersNewYorkThreeBanchesTest() {
        FranchiseService service = new FranchiseService(franchiseCoreService);
        GameRound round = franchiseCoreService.init(List.of(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.BLACK));
        round.getPlates().put(City.NEW_YORK, new CityPlate(false, List.of(PlayerColor.BLUE, PlayerColor.BLUE, PlayerColor.BLACK)));
        int openPlates = service.openPlates(round);
        Assertions.assertEquals(121 - 3, openPlates);
    }

    @Test
    void evaluateGamePhase2PlayerStartTest() {
        FranchiseService service = new FranchiseService(franchiseCoreService);
        GameRound round = franchiseCoreService.init(List.of(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.BLACK));
        Assertions.assertEquals(GamePhase.START, service.evaluateGamePhase(round));
    }

}

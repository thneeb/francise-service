package de.neebs.franchise.control;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
class ReinforcementLearningTest {
    private final FranchiseCoreService franchiseCoreService = new FranchiseCoreService();

    @Test
    void test() {
        GameRound round = franchiseCoreService.init(List.of(PlayerColor.BLUE, PlayerColor.RED));
        FranchiseRLService service = new FranchiseRLService(franchiseCoreService);
        service.setup(true);
        Map<PlayerColor, Integer> winners = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            PlayerColor winner = service.play(round, Map.of(PlayerColor.BLUE, 0.9f, PlayerColor.RED, 0.9f));
            winners.put(winner, winners.getOrDefault(winner, 0) + 1);
            log.info("Iteration: " + i + ", Winner: " + winner);
        }
        log.info(winners.toString());
        service.play(round, Map.of(PlayerColor.BLUE, 0.9f, PlayerColor.RED, 0.9f));
        service.save();
    }
}

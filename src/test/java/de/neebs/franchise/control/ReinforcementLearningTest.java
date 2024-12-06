package de.neebs.franchise.control;

import org.junit.jupiter.api.Test;

import java.util.List;

class ReinforcementLearningTest {
    private final FranchiseCoreService franchiseCoreService = new FranchiseCoreService();

    @Test
    void test() {
        GameRound round = franchiseCoreService.init(List.of(PlayerColor.BLUE, PlayerColor.RED));
        FranchiseRLService service = new FranchiseRLService(franchiseCoreService);
        service.setup(true);
        for (int i = 0; i < 400; i++) {
            service.play(round, 0);
        }
        service.play(round, 0.9f);
        service.save();
    }
}

package de.neebs.franchise.control;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class InMemoryMonteCarloLearningModel {
    private final Map<GameRound, Map<Draw, List<Integer>>> learnings = new HashMap<>();

    public void learn(GameRound gameRound, Draw draw, Map<PlayerColor, Integer> playerScores) {
        Map<Draw, List<Integer>> map = learnings.computeIfAbsent(gameRound, k -> new HashMap<>());
        List<Integer> list = map.computeIfAbsent(draw, k -> new ArrayList<>());
        list.add(playerScores.get(gameRound.getNext()));
    }

    public Map<Draw, List<Integer>> getLearnings(GameRound round) {
        return learnings.get(round);
    }
}

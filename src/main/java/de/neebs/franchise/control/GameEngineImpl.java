package de.neebs.franchise.control;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameEngineImpl implements GameEngine {
    private final FranchiseService franchiseService;

    private final FranchiseRLService franchiseRLService;

    private final FranchiseCoreService franchiseCoreService;

    private final FranchiseMLService franchiseMLService;

    private final InMemoryMonteCarloLearningModel inMemoryMonteCarloLearningModel;

    private class RLComputerPlayer extends AbstractComputerPlayer {
        public static final String EPSILON = "epsilon";

        RLComputerPlayer(PlayerColor playerColor, Map<String, Object> params) {
            super(playerColor, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseRLService.reinforcementLearning(round, getFloat(getParams(), EPSILON, 0.9f));
        }
    }

    private class MachineLearningComputerPlayer extends AbstractComputerPlayer {
        public static final String RANGE = "range";

        MachineLearningComputerPlayer(PlayerColor playerColor, Map<String, Object> params) {
            super(playerColor, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseMLService.machineLearning(round, getInt(getParams(), RANGE, 3));
        }
    }

    private class MiniMaxComputerPlayer extends AbstractComputerPlayer {
        public static final String DEPTH = "depth";

        MiniMaxComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseService.minimax(round, getInt(getParams(), DEPTH, 3));
        }
    }

    private class FindBestDrawComputerPlayer extends AbstractComputerPlayer {
        private static final String DEPTH = "depth";

        FindBestDrawComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseService.findBestMove(round, getInt(getParams(), DEPTH, 3));
        }
    }

    private class MinimaxAbPruneComputerPlayer extends AbstractComputerPlayer {
        private static final String DEPTH = "depth";

        MinimaxAbPruneComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseService.minimaxAbPrune(round, getInt(getParams(), DEPTH, 3));
        }
    }

    private class DivideAndConquerComputerPlayer extends AbstractComputerPlayer {
        private static final String DEPTH = "depth";
        private static final String SLICE = "slice";

        DivideAndConquerComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseService.divideAndConquer(round, getInt(getParams(), DEPTH, 3), getInt(getParams(), SLICE, 3));
        }
    }

    private class MonteCarloComputerPlayer extends AbstractComputerPlayer {
        public static final String EPSILON = "epsilon";

        MonteCarloComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            Map<Draw, List<Integer>> learnings = inMemoryMonteCarloLearningModel.getLearnings(round);
            return franchiseService.computerDraw(round, learnings, getFloat(getParams(), EPSILON, 0.9f));
        }
    }

    private class MonteCarloLearningModel implements LearningModel {
        @Override
        public void train(List<GameRoundDraw> gameRoundDraws) {
            for (GameRoundDraw grd : gameRoundDraws) {
                inMemoryMonteCarloLearningModel.learn(grd.getGameRound(), grd.getDraw(), franchiseCoreService.score(grd.getGameRound().getScores()));
            }
        }

        @Override
        public void save() {
            // do nothing
        }
    }

    private class MachineLearningModel implements LearningModel {
        @Override
        public void train(List<GameRoundDraw> gameRoundDraws) {
            franchiseMLService.train(gameRoundDraws);
        }

        @Override
        public void save() {
            franchiseMLService.save();
        }
    }

    private class ReinforcementLearningModel implements LearningModel {
        @Override
        public void train(List<GameRoundDraw> gameRoundDraws) {
            franchiseRLService.learn(gameRoundDraws);
        }

        @Override
        public void save() {
            franchiseRLService.save();
        }
    }

    @Override
    public GameRound initGame(List<PlayerColor> players) {
        return franchiseCoreService.init(players);
    }

    @Override
    public ComputerPlayer createComputerPlayer(Algorithm algorithm, PlayerColor playerColor, Map<String, Object> params) {
        if (algorithm == Algorithm.REINFORCEMENT_LEARNING) {
            return new RLComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.MACHINE_LEARNING) {
            return new MachineLearningComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.MINIMAX) {
            return new MiniMaxComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.MINIMAX_AB_PRUNE) {
            return new MinimaxAbPruneComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.DIVIDE_AND_CONQUER) {
            return new DivideAndConquerComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.FIND_BEST_MOVE) {
            return new FindBestDrawComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.MONTE_CARLO) {
            return new MonteCarloComputerPlayer(playerColor, params);
        } else {
            throw new IllegalArgumentException("Unknown algorithm");
        }
    }

    @Override
    public LearningModel createLearningModel(Algorithm algorithm) {
        if (algorithm == Algorithm.MONTE_CARLO) {
            return new MonteCarloLearningModel();
        } else if (algorithm == Algorithm.REINFORCEMENT_LEARNING) {
            return new ReinforcementLearningModel();
        } else if (algorithm == Algorithm.MACHINE_LEARNING){
            return new MachineLearningModel();
        } else {
            throw new IllegalArgumentException("Unknown algorithm");
        }
    }

    @Override
    public Map<PlayerColor, Integer> play(GameRound round, Set<ComputerPlayer> players, Set<LearningModel> learningModels, Map<String, Object> params, int times) {
        Map<PlayerColor, Integer> result = new EnumMap<>(PlayerColor.class);
        for (int i = 0; i < times; i++) {
            List<GameRoundDraw> grds = play(round, players);
            Map<PlayerColor, Integer> map = grds.get(grds.size() - 1).getGameRound().getScores().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().getInfluence()));
            log.info("Result: {}: {}", i, map);
            for (LearningModel learningModel : learningModels) {
                learningModel.train(grds);
            }
            PlayerColor winner = map.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            if (map.values().stream().distinct().count() != 1) {
                result.put(winner, result.getOrDefault(winner, 0) + 1);
            }
        }
        for (LearningModel learningModel : learningModels) {
            learningModel.save();
        }
        return result;
    }

    @Override
    public List<GameRoundDraw> play(GameRound round, Set<ComputerPlayer> players) {
        List<GameRoundDraw> grds = new ArrayList<>();
        while (!round.isEnd()) {
            PlayerColor next = round.getNext();
            ComputerPlayer player = players.stream().filter(f -> f.getPlayerColor().equals(next)).findAny().orElseThrow();
            Draw draw = player.evaluateDraw(round);
            grds.add(GameRoundDraw.builder().gameRound(round).draw(draw).build());
            round = makeDraw(round, draw).getGameRound();
        }
        grds.add(GameRoundDraw.builder().gameRound(round).draw(null).build());

        return grds;
    }

    @Override
    public ExtendedGameRound makeDraw(GameRound round, Draw draw) {
        return franchiseCoreService.manualDraw(round, draw);
    }

    @Override
    public List<Draw> nextPossibleDraws(GameRound round) {
        return franchiseCoreService.nextDraws(round);
    }

    private int getInt(Map<String, Object> map, String name, int defaultValue) {
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

    private float getFloat(Map<String, Object> map, String name, float defaultValue) {
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

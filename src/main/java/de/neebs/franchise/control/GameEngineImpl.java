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

    private final FranchiseQLService franchiseQLService;

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
            return franchiseService.maximax(round, getInt(getParams(), DEPTH, 3));
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
            return franchiseService.divideAndConquer(round, getInt(getParams(), DEPTH, 2), getInt(getParams(), SLICE, 2));
        }
    }

    private class MonteCarloComputerPlayer extends AbstractComputerPlayer {
        private static final String EPSILON = "epsilon";
        private static final String TIMES = "times";
        private final boolean playGame;

        MonteCarloComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
            playGame = true;
        }

        MonteCarloComputerPlayer(PlayerColor color, Map<String, Object> params, boolean playGame) {
            super(color, params);
            this.playGame = playGame;
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            if (playGame) {
                play(round, createPlayers(round.getPlayers()), Set.of(new MonteCarloLearningModel()), Map.of(), getInt(getParams(), TIMES, 100));
            }
            Map<Draw, List<Integer>> learnings = inMemoryMonteCarloLearningModel.getLearnings(round);
            return franchiseService.monteCarloTreeSearch(round, learnings, getFloat(getParams(), EPSILON, 0.95f));
        }

        private Set<ComputerPlayer> createPlayers(List<PlayerColor> players) {
            return players.stream().map(f -> new MonteCarloComputerPlayer(f, getParams(), false)).collect(Collectors.toSet());
        }
    }

    private class QLearningComputerPlayer extends AbstractComputerPlayer {
        private static final String EPSILON = "epsilon";

        QLearningComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseQLService.qLearning(round, getFloat(getParams(), EPSILON, 0.9f));
        }
    }

    private class QLearningModel extends AbstractLearningModel {
        private static final String GAMMA = "gamma";
        private static final String LEARNING_RATE = "lr";

        QLearningModel(Map<String, Object> params) {
            super(params);
        }

        @Override
        public void train(List<GameRoundDraw> gameRoundDraws) {
            franchiseQLService.train(gameRoundDraws, getFloat(getParams(), GAMMA, 0.9f), getFloat(getParams(), LEARNING_RATE, 0.1f));
        }

        @Override
        public void save() {
            // do nothing
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
        } else if (algorithm == Algorithm.MAXIMAX) {
            return new FindBestDrawComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.MONTE_CARLO_TREE_SEARCH) {
            return new MonteCarloComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.Q_LEARNING) {
            return new QLearningComputerPlayer(playerColor, params);
        } else {
            throw new IllegalArgumentException("Unknown algorithm");
        }
    }

    @Override
    public LearningModel createLearningModel(Algorithm algorithm, Map<String, Object> params) {
        if (algorithm == Algorithm.MONTE_CARLO_TREE_SEARCH) {
            return new MonteCarloLearningModel();
        } else if (algorithm == Algorithm.REINFORCEMENT_LEARNING) {
            return new ReinforcementLearningModel();
        } else if (algorithm == Algorithm.MACHINE_LEARNING){
            return new MachineLearningModel();
        } else if (algorithm == Algorithm.Q_LEARNING) {
            return new QLearningModel(params);
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
}

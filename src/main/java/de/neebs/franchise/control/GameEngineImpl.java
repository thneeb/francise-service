package de.neebs.franchise.control;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GameEngineImpl implements GameEngine {
    private final FranchiseService franchiseService;

    private final FranchiseRLService franchiseRLService;

    private final FranchiseCoreService franchiseCoreService;

    private final InMemoryMonteCarloLearningModel inMemoryMonteCarloLearningModel;

    private class RLComputerPlayer extends AbstractComputerPlayer {
        public static final String EPSILON = "epsilon";

        RLComputerPlayer(PlayerColor playerColor, Map<String, Object> params) {
            super(playerColor, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseRLService.reinforcementLearning(round, getFloat(getParams().get(EPSILON), 0.9f));
        }
    }

    private class MiniMaxComputerPlayer extends AbstractComputerPlayer {
        public static final String DEPTH = "depth";

        MiniMaxComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseService.minimax(round, getParams().get(DEPTH) == null ? 3 : (Integer)getParams().get(DEPTH));
        }
    }

    private class FindBestDrawComputerPlayer extends AbstractComputerPlayer {
        private static final String DEPTH = "depth";

        FindBestDrawComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseService.findBestMove(round, getParams().get(DEPTH) == null ? 3 : (Integer)getParams().get(DEPTH));
        }
    }

    private class MinimaxAbPruneComputerPlayer extends AbstractComputerPlayer {
        private static final String DEPTH = "depth";

        MinimaxAbPruneComputerPlayer(PlayerColor color, Map<String, Object> params) {
            super(color, params);
        }

        @Override
        public Draw evaluateDraw(GameRound round) {
            return franchiseService.minimaxAbPrune(round, getParams().get(DEPTH) == null ? 3 : (Integer)getParams().get(DEPTH));
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
            return franchiseService.divideAndConquer(round,
                    getParams().get(DEPTH) == null ? 3 : (Integer)getParams().get(DEPTH),
                    getParams().get(SLICE) == null ? 3 : (Integer)getParams().get(SLICE)
            );
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
            return franchiseService.computerDraw(round, learnings, getFloat(getParams().get(EPSILON), 0.9f));
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
        } else if (algorithm == Algorithm.MINIMAX) {
            return new MiniMaxComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.MINIMAX_AB_PRUNE) {
            return new MinimaxAbPruneComputerPlayer(playerColor, params);
        }else if (algorithm == Algorithm.DIVIDE_AND_CONQUER) {
            return new DivideAndConquerComputerPlayer(playerColor, params);
        } else if (algorithm == Algorithm.FIND_BEST_MOVE) {
            return new FindBestDrawComputerPlayer(playerColor, params);
        }
        return null;
    }

    @Override
    public LearningModel createLearningModel(Algorithm algorithm) {
        if (algorithm == Algorithm.MONTE_CARLO) {
            return new MonteCarloLearningModel();
        } else if (algorithm == Algorithm.REINFORCEMENT_LEARNING) {
            return new ReinforcementLearningModel();
        }
        return null;
    }

    @Override
    public void play(GameRound round, Set<ComputerPlayer> players, Set<LearningModel> learningModels, Map<String, Object> params, int times) {
        for (int i = 0; i < times; i++) {
            List<GameRoundDraw> grds = play(round, players);
            for (LearningModel learningModel : learningModels) {
                learningModel.train(grds);
            }
        }
        for (LearningModel learningModel : learningModels) {
            learningModel.save();
        }
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

    private float getFloat(Object o, float defaultValue) {
        if (o instanceof Float value) {
            return value;
        } else if (o instanceof Double value) {
            return value.floatValue();
        } else {
            return defaultValue;
        }
    }
}

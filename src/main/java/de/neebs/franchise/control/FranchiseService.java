package de.neebs.franchise.control;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FranchiseService {
    private static final Random RANDOM = new Random();

    private final Map<GameRound, Map<Draw, List<Integer>>> learnings = new HashMap<>();

    private final FranchiseCoreService franchiseCoreService;

    private List<GameRoundDrawPredecessor> nextRounds(GameRound gameRound, int count) {
        List<GameRoundDrawPredecessor> set = List.of(GameRoundDrawPredecessor.builder().gameRound(new ExtendedGameRound(gameRound, null)).build());
        for (int i = 0; i < count && set.size() < 25000; i++) {
            List<GameRoundDrawPredecessor> intermediate = new ArrayList<>();
            for (GameRoundDrawPredecessor round : set) {
                intermediate.addAll(nextRound2(round.getGameRound().getGameRound()));
            }
            set = intermediate;
            log.info("Round " + (i + 1) + ": " + set.size());
        }
        for (GameRoundDrawPredecessor round : set) {
            franchiseCoreService.scoreRound(round.getGameRound().getGameRound(), null);
        }
        log.info("Scored: " + set.size());
        return set;
    }

    private List<GameRoundDrawPredecessor> nextRound2(GameRound gameRound) {
        List<GameRoundDrawPredecessor> rounds = new ArrayList<>();
        List<Draw> draws = franchiseCoreService.nextDraws(gameRound);
        for (Draw draw : draws) {
            rounds.add(GameRoundDrawPredecessor.builder().draw(draw).gameRound(franchiseCoreService.manualDraw(gameRound, draw)).build());
        }
        return rounds;
    }

    private Optional<Draw> filterDrawsByLearnings(GameRound round, List<Draw> draws, Map<Draw, List<Integer>> learnings, float epsilon) {
        if (learnings == null) {
            return Optional.empty();
        }
        List<Draw> bestDraws = draws.stream()
                .filter(f -> learnings.get(f) != null)
                .collect(Collectors.toMap(Function.identity(), f -> learnings.get(f).stream().mapToInt(g -> g).average().orElseThrow()))
                .entrySet().stream().sorted((o1, o2) -> -Double.compare(o1.getValue(), o2.getValue()))
                .map(Map.Entry::getKey).toList();
        if (bestDraws.isEmpty()) {
            return Optional.empty();
        }
        if (RANDOM.nextDouble(1) < epsilon) {
            log.info("Hit " + round.getRound());
            return Optional.of(bestDraws.get(RANDOM.nextInt(Math.min(bestDraws.size(), 6))));
        } else {
            return Optional.empty();
        }
    }

    int openPlates(GameRound round) {
        return Arrays.stream(City.values())
                .filter(f -> round.getPlayers().size() > 2 || round.getPlates().get(f) == null || round.getPlates().get(f).getBranches().get(0) != PlayerColor.BLACK)
                .filter(f -> round.getPlates().get(f) == null || !round.getPlates().get(f).isClosed())
                .mapToInt(f -> f.getSize() - (round.getPlates().get(f) == null ? 0 : round.getPlates().get(f).getBranches().size()))
                .sum();
    }

    /**
     * Evaluates the game phase by calculating the percentage of the open branch positions devided through the possible
     * branches
     * @param round a game round
     * @return percentage (0-100)
     */
    GamePhase evaluateGamePhase(GameRound round) {
        int openPlates = openPlates(round) * 100 / (round.getPlayers().size() == 2 ? 94 : 121);
        if (openPlates <= 30) {
            return GamePhase.END;
        } else if (openPlates >= 75) {
            return GamePhase.START;
        } else {
            return GamePhase.GROW;
        }
    }

    private void scoreIncome(GameRound round, int divisor) {
        for (Map.Entry<PlayerColor, Score> entry : round.getScores().entrySet()) {
            Score score = entry.getValue();
            int influence = (franchiseCoreService.calcIncomeScore(entry.getKey(), round.getPlates())) / divisor;
            score.setInfluence(score.getInfluence() + influence);
        }
    }

    public Draw minimax(GameRound round, int depth) {
        List<GameRoundDrawPredecessor> rounds = nextRounds(round, depth);
        Map<GameRoundDrawPredecessor, Map<PlayerColor, Integer>> scores = score(rounds);
        Map<GameRoundDrawPredecessor, GameRoundDrawPredecessor> bestMoves = new HashMap<>();
        while (scores.size() > 1) {
            Map<GameRoundDrawPredecessor, Optional<Map.Entry<GameRoundDrawPredecessor, Map<PlayerColor, Integer>>>> scores2;
            if (scores.entrySet().iterator().next().getKey().getGameRound().getGameRound().getActual() == round.getNext()) {
                scores2 = scores.entrySet().stream().collect(
                        Collectors.groupingBy(f -> f.getKey().getPredecessor(),
                                Collectors.reducing((a, b) -> a.getValue().get(round.getNext()) > b.getValue().get(round.getNext()) ? a : b)));
            } else {
                scores2 = scores.entrySet().stream().collect(
                        Collectors.groupingBy(f -> f.getKey().getPredecessor(),
                                Collectors.reducing((a, b) -> a.getValue().get(round.getNext()) < b.getValue().get(round.getNext()) ? a : b)));
            }
            scores = scores2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().orElseThrow().getValue()));
            bestMoves = scores2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().orElseThrow().getKey()));
        }
        return bestMoves.values().iterator().next().getDraw();
    }

    public Draw findBestMove(GameRound round, int depth) {
        List<GameRoundDrawPredecessor> rounds = nextRounds(round, depth);
        Map<GameRoundDrawPredecessor, Map<PlayerColor, Integer>> scores = score(rounds);
        Map<GameRoundDrawPredecessor, GameRoundDrawPredecessor> bestMoves = new HashMap<>();
        while (scores.size() > 1) {
            Map<GameRoundDrawPredecessor, Optional<Map.Entry<GameRoundDrawPredecessor, Map<PlayerColor, Integer>>>> scores2 = scores.entrySet().stream()
                    .collect(
                            Collectors.groupingBy(f -> f.getKey().getPredecessor(),
                                    Collectors.reducing((a, b) -> a.getValue().get(a.getKey().getGameRound().getGameRound().getActual()) > b.getValue().get(b.getKey().getGameRound().getGameRound().getActual()) ? a : b)));
            scores = scores2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().orElseThrow().getValue()));
            bestMoves = scores2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().orElseThrow().getKey()));
        }
        return bestMoves.values().iterator().next().getDraw();
    }

    private Map<GameRoundDrawPredecessor, Map<PlayerColor, Integer>> score(List<GameRoundDrawPredecessor> rounds) {
        Map<GameRoundDrawPredecessor, Map<PlayerColor, Integer>> map = new HashMap<>();
        for (GameRoundDrawPredecessor round : rounds) {
            Map<PlayerColor, Integer> scores = franchiseCoreService.score(round.getGameRound().getGameRound().getScores());
            map.put(round, scores);
        }
        return map;
    }

    public Draw computerDraw(GameRound gameRound, Map<Draw, List<Integer>> learnings, float epsilon) {
        List<Draw> draws = franchiseCoreService.nextDraws(gameRound);
        if (learnings == null || learnings.isEmpty()) {
            return draws.get(RANDOM.nextInt(draws.size()));
        } else {
            return filterDrawsByLearnings(gameRound, draws, learnings, epsilon).orElse(draws.get(RANDOM.nextInt(draws.size())));
        }
    }

    private List<Draw> filterAndSortDraws(GameRound round, List<Draw> draws) {
        GamePhase phase = evaluateGamePhase(round);
        if (phase == GamePhase.START) {
            draws = draws.stream()
                    .filter(f -> f.getBonusTileUsage() != BonusTileUsage.INCREASE)
                    .filter(f -> !f.getExtension().isEmpty())
                    .toList();
        }
        return draws.stream().sorted((o1, o2) -> Boolean.compare(o2.isBonusTile(), o1.isBonusTile())).toList();
    }

    public Draw minimaxAbPrune(GameRound round, int depth) {
        Map<PlayerColor, Integer> alpha = new EnumMap<>(PlayerColor.class);
        Map<PlayerColor, Integer> beta = new EnumMap<>(PlayerColor.class);
        for (PlayerColor color : round.getPlayers()) {
            alpha.put(color, -10000);
            beta.put(color, 10000);
        }
        return minimaxAbPrune2(round, round.getNext(), depth, alpha, beta, new HashMap<>(), null).getDraw();
    }

    private ScoredDraw minimaxAbPrune2(GameRound round, PlayerColor actual, int depth, Map<PlayerColor, Integer> alpha, Map<PlayerColor, Integer> beta, Map<Integer, Integer> savedScores, List<ScoredDraw> scoredDraws) {
        if (depth == 0 || round.isEnd()) {
            int score = evaluatePosition(round, actual);
            return ScoredDraw.builder().gameRound(round).score(score).build();
        }

        int extremeScore = round.getNext() == actual ? -10000 : +10000;
        ScoredDraw bestMove = null;
        for (Draw draw : filterAndSortDraws(round, franchiseCoreService.nextDraws(round))) {
            GameRound newBoard = franchiseCoreService.manualDraw(round, draw).getGameRound();
            final ScoredDraw scoredDraw;
            if (savedScores.get(newBoard.hashCode()) == null) {
                scoredDraw = minimaxAbPrune2(newBoard, actual, depth - 1, alpha, beta, savedScores, null);
                if (newBoard.getActual() == actual) {
                    if (scoredDraw.getScore() > extremeScore) {
                        extremeScore = scoredDraw.getScore();
                        bestMove = scoredDraw;
                    }
                    if (scoredDraw.getScore() > alpha.get(newBoard.getActual())) {
                        alpha.put(newBoard.getActual(), scoredDraw.getScore());
                    }
                } else {
                    if (scoredDraw.getScore() < extremeScore) {
                        extremeScore = scoredDraw.getScore();
                        bestMove = scoredDraw;
                    }
                    if (scoredDraw.getScore() < beta.get(newBoard.getActual())) {
                        beta.put(newBoard.getActual(), scoredDraw.getScore());
                    }
                }
                savedScores.put(newBoard.hashCode(), scoredDraw.getScore());
            } else {
                scoredDraw = ScoredDraw.builder().score(savedScores.get(newBoard.hashCode())).build();
            }
            scoredDraw.setDraw(draw);
            if (scoredDraws != null) {
                scoredDraws.add(scoredDraw);
            }
            if (alpha.get(newBoard.getActual()) > beta.get(newBoard.getActual())) {
                break;
            }
        }
        if (bestMove == null) {
            return ScoredDraw.builder().score(extremeScore).build();
        } else {
            return bestMove;
        }
    }

    private ScoredDraw minimaxAbPrune(GameRound round, PlayerColor actual, int depth, int alpha, int beta, Map<Integer, Integer> savedScores, List<ScoredDraw> scoredDraws) {
        if (depth == 0 || round.isEnd()) {
            int score = evaluatePosition(round, actual);
//            score = round.getNext() == actual ? -score : score;
            return ScoredDraw.builder().gameRound(round).score(score).build();
        }

        if (round.getRound() <= round.getPlayers().size() * 2) {
            alpha = -10000;
            beta = 10000;
        }

        ScoredDraw bestMove = null;
        for (Draw draw : filterAndSortDraws(round, franchiseCoreService.nextDraws(round))) {
            GameRound newBoard = franchiseCoreService.manualDraw(round, draw).getGameRound();
            Integer score = savedScores.get(newBoard.hashCode());
            ScoredDraw scoredDraw;
            if (score == null) {
                if ((newBoard.getActual() == actual || newBoard.getNext() == actual) && newBoard.getActual() != newBoard.getNext()) {
                    scoredDraw = minimaxAbPrune(newBoard, actual, depth - 1, -beta, -alpha, savedScores, null);
                    score = -scoredDraw.getScore();
                } else {
                    scoredDraw = minimaxAbPrune(newBoard, actual, depth - 1, alpha, beta, savedScores, null);
                    score = scoredDraw.getScore();
                }
                savedScores.put(newBoard.hashCode(), score);
            } else {
                scoredDraw = ScoredDraw.builder().score(score).gameRound(newBoard).build();
            }
            scoredDraw.setScore(score);
            scoredDraw.setDraw(draw);
            if (scoredDraws != null) {
                scoredDraws.add(scoredDraw);
            }
            if (score > alpha) {
                alpha = score;
                bestMove = scoredDraw;
                if (alpha >= beta) {
                    break;
                }
            }
        }
        if (bestMove == null) {
            return ScoredDraw.builder().score(alpha).build();
        } else {
            return bestMove;
        }
    }

    private int evaluatePosition(GameRound round, PlayerColor color) {
        GamePhase phase = evaluateGamePhase(round);
        franchiseCoreService.scoreTowns(round, null);
        franchiseCoreService.scoreMoney(round, null, switch (phase) {
            case START -> 6;
            case GROW, END -> 3;
        });
        franchiseCoreService.scoreBonusTiles(round, null, switch (phase) {
            case START -> 1;
            case GROW -> 2;
            case END -> 4;
        });
        scoreIncome(round, switch (phase) {
            case START -> 2;
            case GROW -> 4;
            case END -> 5;
        });
        scoreRegionPossession(round);
        return franchiseCoreService.score(round.getScores()).get(color);
    }

    private void scoreRegionPossession(GameRound round) {
        for (PlayerColor playerColor : round.getPlayers()) {
            Set<City> ownedCities = franchiseCoreService.retrieveOwnedCities(round.getPlates(), playerColor);
            int possessedRegions = (int)Arrays.stream(Region.values())
                    .filter(f -> ownedCities.stream().anyMatch(g -> f.getCities().contains(g))).count();
            Score score = round.getScores().get(playerColor);
            score.setInfluence(score.getInfluence() + possessedRegions * 1);
        }
    }

    public Draw divideAndConquer(GameRound round, int depth, int slice) {
        List<ScoredDraw> scoredDraws = new ArrayList<>();
        minimaxAbPrune(round, round.getNext(), depth, -10000, +10000, new HashMap<>(), scoredDraws);
        scoredDraws.sort((o1, o2) -> -Integer.compare(o1.getScore(), o2.getScore()));
        if (slice == 0) {
            return scoredDraws.get(0).getDraw();
        } else {
            ScoredDraw result = null;
            for (ScoredDraw sd : scoredDraws.stream().filter(f -> f.getGameRound() != null).limit(Math.min(scoredDraws.size(), slice)).toList()) {
                ScoredDraw draw = minimaxAbPrune(sd.getGameRound(), sd.getGameRound().getNext(), depth, -10000, +10000, new HashMap<>(), new ArrayList<>());
                if (result == null
                        || (result.getScore() < draw.getScore() && depth % round.getPlayers().size() == 0)
                        || (result.getScore() > draw.getScore() && depth % round.getPlayers().size() > 0)) {
                    result = sd;
                }
            }
            assert result != null;
            log.info(result.toString());
            return result.getDraw();
        }
    }

    @Getter
    @Setter
    @Builder
    @ToString
    private static class ScoredDraw {
        private Draw draw;
        private GameRound gameRound;
        private int score;
    }

    @Getter
    @Setter
    @Builder
    @EqualsAndHashCode
    private static class GameRoundDrawPredecessor {
        private GameRoundDrawPredecessor predecessor;
        private ExtendedGameRound gameRound;
        private Draw draw;
    }

    enum GamePhase {
        START,
        GROW,
        END
    }

}

package de.neebs.franchise.control;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FranchiseQLService {
    private static final Random RANDOM = new Random();

    private final FranchiseCoreService franchiseCoreService;

    private final Map<QState, Map<Draw, Double>> qValues = new HashMap<>(); // <state, <draw, value>>

    public Draw qLearning(GameRound round, float epsilon) {
        List<Draw> possibleDraws = franchiseCoreService.nextDraws(round);
        if (RANDOM.nextFloat() < epsilon) {
            Map<Draw, Double> draws = qValues.computeIfAbsent(reduceState(round), k -> new HashMap<>());
            Optional<Map.Entry<Draw, Double>> entry = draws.entrySet().stream()
                    .filter(e -> possibleDraws.contains(e.getKey()))
                    .max(Comparator.comparingDouble(Map.Entry::getValue));
            if (entry.isPresent()) {
//                log.info("Hit");
                return entry.get().getKey();
            } else {
//                log.info("Miss");
                return possibleDraws.get(RANDOM.nextInt(possibleDraws.size()));
            }
        } else {
//            log.info("Random");
            return possibleDraws.get(RANDOM.nextInt(possibleDraws.size()));
        }
    }

    private QState reduceState(GameRound round) {
        return QState.builder()
                .ownBranches(Arrays.stream(City.values()).collect(Collectors.toMap(city -> city, city -> round.getPlates().getOrDefault(city, new CityPlate(false, List.of(), null)).getBranches().stream().filter(f -> f == round.getNext()).count())))
                .opponentBranches(Arrays.stream(City.values()).collect(Collectors.toMap(city -> city, city -> round.getPlates().getOrDefault(city, new CityPlate(false, List.of(), null)).getBranches().stream().filter(f -> f != round.getNext()).count())))
                .money(round.getScores().get(round.getNext()).getMoney())
                .influence(round.getScores().get(round.getNext()).getInfluence())
                .bonusTiles(round.getScores().get(round.getNext()).getBonusTiles())
                .build();
    }

    public void train(List<GameRoundDraw> gameRoundDraws, float gamma, float learningRate) {
        for (int i = 0; i < gameRoundDraws.size(); i++) {
            GameRoundDraw gameRoundDraw = gameRoundDraws.get(i);
            GameRound round = gameRoundDraw.getGameRound();
            if (round.isEnd()) {
                continue;
            }
            Draw draw = gameRoundDraw.getDraw();
            PlayerColor player = round.getNext();
            QState state = reduceState(round);
            GameRound nextRound = gameRoundDraws.get(i + 1).getGameRound();
            int reward = nextRound.getScores().get(player).getInfluence() - round.getScores().get(player).getInfluence();
            QState nextState = reduceState(nextRound);
            Map<Draw, Double> draws = qValues.computeIfAbsent(state, k -> new HashMap<>());
            double q = draws.computeIfAbsent(draw, k -> 0d);
            double maxQ = qValues.computeIfAbsent(nextState, k -> new HashMap<>()).values().stream().max(Double::compare).orElse(0d);
            q = q + learningRate * (reward + gamma * maxQ - q);
            draws.put(draw, q);
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode
    private static class QState {
        private Map<City, Long> ownBranches = new EnumMap<>(City.class); // <city, count>
        private Map<City, Long> opponentBranches = new EnumMap<>(City.class); // <city, count>
        private int money;
        private int influence;
        private int bonusTiles;
    }
}

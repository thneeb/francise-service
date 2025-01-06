package de.neebs.franchise.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FranchiseQLService {
    private static final Random RANDOM = new Random();

    private final ObjectMapper objectMapper;

    private final FranchiseCoreService franchiseCoreService;

    private final QValuePersistence qValues = new QValueFilePersistence();

    public Draw qLearning(GameRound round, float epsilon) {
        List<Draw> possibleDraws = franchiseCoreService.nextDraws(round);
        if (RANDOM.nextFloat() < epsilon) {
            QState state = reduceState(round);
            Map<Draw, Double> draws = qValues.getQValues(state).get(state);
            Optional<Map.Entry<Draw, Double>> entry = draws.entrySet().stream()
                    .filter(e -> possibleDraws.contains(e.getKey()))
                    .max(Comparator.comparingDouble(Map.Entry::getValue));
            if (entry.isPresent()) {
                return entry.get().getKey();
            } else {
                return possibleDraws.get(RANDOM.nextInt(possibleDraws.size()));
            }
        } else {
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
            Map<QState, Map<Draw, Double>> qv = qValues.getQValues(state);
            Map<Draw, Double> draws = qv.get(state);
            double q = draws.computeIfAbsent(draw, k -> 0d);
            double maxQ = qValues.getQValues(nextState).get(nextState).values().stream().max(Double::compare).orElse(0d);
            q = q + learningRate * (reward + gamma * maxQ - q);
            draws.put(draw, q);
            qValues.persist(qv);
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

    private interface QValuePersistence {
        Map<QState, Map<Draw, Double>> getQValues(QState state);

        void persist(Map<QState, Map<Draw, Double>> qValues);
    }

    private static class QValueMemoryPersistence implements QValuePersistence {
        private final Map<QState, Map<Draw, Double>> qValues = new HashMap<>(); // <state, <draw, value>>

        @Override
        public Map<QState, Map<Draw, Double>> getQValues(QState state) {
            qValues.computeIfAbsent(state, k -> new HashMap<>());
            return qValues;
        }

        @Override
        public void persist(Map<QState, Map<Draw, Double>> qValues) {
            // nothing to do
        }
    }

    private class QValueFilePersistence implements QValuePersistence {
        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor(access = AccessLevel.PRIVATE)
        private static class SavedDrawValue {
            private Draw draw;
            private Double value;
        }

        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor(access = AccessLevel.PRIVATE)
        private static class SavedQValue {
            private QState state;
            private List<SavedDrawValue> qValues; // <draw, value>
        }

        @Override
        public Map<QState, Map<Draw, Double>> getQValues(QState state) {
            try {
                String s = Files.readString(Path.of("qlearning", state.hashCode() + ".json"));
                List<SavedQValue> savedQValue = objectMapper.readValue(s, new TypeReference<>() {});
                Map<QState, Map<Draw, Double>> qv = savedQValue.stream().collect(Collectors.toMap(SavedQValue::getState, e -> e.getQValues().stream().collect(Collectors.toMap(SavedDrawValue::getDraw, SavedDrawValue::getValue))));
                qv.computeIfAbsent(state, k -> new HashMap<>());
                return qv;
            } catch (IOException e) {
                return Map.of(state, new HashMap<>());
            }
        }

        @Override
        public void persist(Map<QState, Map<Draw, Double>> qValues) {
            List<SavedQValue> savedQValues = qValues.entrySet().stream().map(e -> {
                SavedQValue savedQValue = new SavedQValue();
                savedQValue.setState(e.getKey());
                savedQValue.setQValues(e.getValue().entrySet().stream().map(f -> {
                    SavedDrawValue savedDrawValue = new SavedDrawValue();
                    savedDrawValue.setDraw(f.getKey());
                    savedDrawValue.setValue(f.getValue());
                    return savedDrawValue;
                }).toList());
                return savedQValue;
            }).toList();
            for (Map.Entry<QState, Map<Draw, Double>> entry : qValues.entrySet()) {
                try {
                    String s = objectMapper.writeValueAsString(savedQValues);
                    Files.write(Path.of("qlearning", entry.getKey().hashCode() + ".json"), s.getBytes(), StandardOpenOption.CREATE);
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
                break;
            }
        }
    }
}

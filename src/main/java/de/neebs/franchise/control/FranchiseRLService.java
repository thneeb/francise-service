package de.neebs.franchise.control;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.training.*;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ai.djl.ndarray.NDManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FranchiseRLService {
    private static final Random RANDOM = new Random();

    private final FranchiseCoreService franchiseCoreService;

    private NDManager manager;

    private ParameterStore parameterStore;

    private Trainer trainer;

    void setup(boolean load) {
        if (manager != null) {
            return;
        }

        manager = NDManager.newBaseManager();
        parameterStore = new ParameterStore(manager, false);

        int countInputDimension = createInputDimension().size();
        Model model = Model.newInstance("policy-network");
        List<Draw> outputs = createOutputDimension();
        SequentialBlock net = new SequentialBlock();
        // count the input params
        net.add(Linear.builder().setUnits(countInputDimension).build()).add(Activation::relu);  // Activation function for non-linearity
        // count the input params
        net.add(Linear.builder().setUnits(150).build()).add(Activation::relu);  // Activation function for non-linearity
        // Output layer with outputDim neurons (number of actions)
        net.add(Linear.builder().setUnits(outputs.size()).build());
        model.setBlock(net);

        if (load) {
            try {
                model.load(Path.of("rl-model"), "rl-model");
            } catch (IOException | MalformedModelException e) {
                throw new IllegalStateException(e);
            }
        }

        TrainingConfig trainerConfig = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())
                .optOptimizer(Optimizer.adam().build());  // Adam optimizer for gradient updates

        trainer = model.newTrainer(trainerConfig);
        trainer.initialize(new Shape(1, countInputDimension));
    }

    public Draw reinforcementLearning(GameRound round, float epsilon) {
        setup(true);

        RatedDraw rd = evaluateDraw(round, epsilon);
        return rd.getDraw();
    }

    public PlayerColor learn(List<GameRoundDraw> gameRoundDraws) {
        setup(true);

        List<Learning> learnings = new ArrayList<>();
        for (GameRoundDraw gameRoundDraw : gameRoundDraws) {
            learnings.add(Learning.builder()
                    .gameRound(gameRoundDraw.getGameRound())
                    .draw(gameRoundDraw.getDraw())
                    .build());
        }

        fillInfluence(learnings);

        buildDifferences(learnings);

        discountRevenues(learnings);

        learnings.remove(learnings.size() - 1);

        trainModel(learnings);

        return learnings.get(learnings.size() - 1).getInfluence().entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    }

    private void fillInfluence(List<Learning> learnings) {
        for (Learning learning : learnings) {
            learning.setInfluence(learning.getGameRound().getScores().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> Float.valueOf(f.getValue().getInfluence()))));
        }
    }

    private void trainModel(List<Learning> learnings) {
        try (GradientCollector collector = trainer.newGradientCollector()) {
            List<Draw> outputs = createOutputDimension();
            for (Learning learning : learnings) {
                if (!learning.getDraw().isNull() && !learning.getDraw().isMoney()) {
                    float[] intInput = convert(createVectorizedBoard(learning.getGameRound(), true));

                    NDArray input = manager.create(intInput);

                    NDArray array = trainer.getModel().getBlock().forward(parameterStore, new NDList(input), true).singletonOrThrow();
                    array = array.log();
                    NDArray totalLoss = manager.create(0f);
                    for (int j = 0; j < array.size(); j++) {
                        if (learning.getDraw().includes(outputs.get(j))) {
                            NDArray actionLogProp = array.get(j);
                            NDArray loss = actionLogProp.mul(-learning.getInfluence().get(learning.getGameRound().getActual() == null ? learning.getGameRound().getNext() : learning.getGameRound().getActual()));
                            totalLoss = totalLoss.add(loss);
                        }
                    }
                    collector.backward(totalLoss);

                    trainer.step();
                }
            }
        }
    }

    private RatedDraw evaluateDraw(GameRound round, float epsilon) {
        List<Draw> outputs = createOutputDimension();

        float[] intInput = convert(createVectorizedBoard(round, true));

        NDArray input = manager.create(intInput);

        NDList list = trainer.getModel().getBlock().forward(parameterStore, new NDList(input), false);

        List<RatedDraw> ratedDraws = new ArrayList<>();
        for (NDArray array : list) {
            for (int j = 0; j < array.size(); j++) {
                ratedDraws.add(RatedDraw.builder().draw(outputs.get(j)).rating(array.get(j).getFloat()).build());
            }
        }
        List<Draw> draws = franchiseCoreService.nextDraws(round);
        List<RatedDraw> ratedPossibleDraws = draws.stream().map(f -> RatedDraw.builder().draw(f).rating(0f).build()).toList();
        for (RatedDraw rd : ratedPossibleDraws) {
            for (RatedDraw rd2 : ratedDraws) {
                if (rd.getDraw().includes(rd2.getDraw())) {
                    rd.setRating(rd.getRating() + rd2.getRating());
                }
            }
            if (rd.getDraw().isNull()) {
                rd.setRating(-Float.MAX_VALUE);
            }
        }

        RatedDraw bestDraw;
        if (RANDOM.nextDouble(1) < epsilon) {
            bestDraw = ratedPossibleDraws.stream().max(Comparator.comparing(RatedDraw::getRating)).orElseThrow();
        } else {
            bestDraw = ratedPossibleDraws.get(RANDOM.nextInt(ratedPossibleDraws.size()));
        }
        return bestDraw;
    }

    private Map<PlayerColor, Float> discountRevenues(List<Learning> learnings) {
        if (learnings.isEmpty()) {
            return Collections.emptyMap();
        }
        learnings.get(0).setInfluence(plus(learnings.get(0).getInfluence(), multiply(discountRevenues(learnings.subList(1, learnings.size())), 0.99f)));
        return learnings.get(0).getInfluence();
    }

    private Map<PlayerColor, Float> multiply(Map<PlayerColor, Float> influence1, float v) {
        if (influence1.isEmpty()) {
            return influence1;
        }
        Map<PlayerColor, Float> influence = new EnumMap<>(influence1);
        for (Map.Entry<PlayerColor, Float> entry : influence.entrySet()) {
            entry.setValue(entry.getValue() * v);
        }
        return influence;
    }

    private Map<PlayerColor, Float> plus(Map<PlayerColor, Float> influence1, Map<PlayerColor, Float> influence2) {
        if (influence2.isEmpty()) {
            return influence1;
        }
        Map<PlayerColor, Float> influence = new EnumMap<>(influence1);
        for (Map.Entry<PlayerColor, Float> entry : influence.entrySet()) {
            entry.setValue(entry.getValue() + influence2.get(entry.getKey()));
        }
        return influence;
    }

    void buildDifferences(List<Learning> learnings) {
        for (int i = 0; i < learnings.size() - 1; i++) {
            Learning learning = learnings.get(i);
            learning.setInfluence(substract(learning.getInfluence(), learnings.get(i + 1).getInfluence()));
        }
    }

    private Map<PlayerColor, Float> substract(Map<PlayerColor, Float> influence1, Map<PlayerColor, Float> influence2) {
        Map<PlayerColor, Float> result = new EnumMap<>(PlayerColor.class);
        for (Map.Entry<PlayerColor, Float> entry : influence1.entrySet()) {
            result.put(entry.getKey(), influence2.get(entry.getKey()) - entry.getValue());
        }
        return result;
    }

    private float[] convert(List<Integer> list) {
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i).floatValue();
        }
        return result;
    }

    private List<Draw> createOutputDimension() {
        List<Draw> result = new ArrayList<>();
        for (City city : City.values()) {
            result.add(Draw.builder().extension(Set.of(city)).build());
        }
        for (City city : City.values()) {
            result.add(Draw.builder().increase(List.of(city)).build());
        }
        return result;
    }

    private List<String> createInputDimension() {
        List<String> result = new ArrayList<>();
        result.add("actual");
        result.add("next");
        for (City city : City.values()) {
            for (int i = 0; i < city.getSize(); i++) {
                result.add(city.name() + i);
            }
        }
        for (PlayerColor color : PlayerColor.values()) {
            result.add(color.name() + "BonusTile");
            result.add(color.name() + "Influence");
            result.add(color.name() + "Money");
        }
        return result;
    }

    private List<Integer> createVectorizedBoard(GameRound round, boolean includePlayerStats) {
        List<Integer> result = new ArrayList<>();
        result.add(round.getActual() == null ? round.getNext().ordinal() : round.getActual().ordinal());
        result.add(round.getNext().ordinal());
        for (City city : City.values()) {
            CityPlate plate = round.getPlates().get(city);
            if (plate == null) {
                plate = new CityPlate(false, new ArrayList<>(), null);
            }
            for (int i = 0; i < city.getSize(); i++) {
                if (i < plate.getBranches().size()) {
                    result.add(plate.getBranches().get(i).ordinal());
                } else {
                    result.add(-1);
                }
            }
        }
        if (includePlayerStats) {
            for (PlayerColor color : PlayerColor.values()) {
                Score score = round.getScores().get(color);
                if (score != null) {
                    result.add(score.getBonusTiles());
                    result.add(score.getInfluence());
                    result.add(score.getMoney());
                } else {
                    result.add(-1);
                    result.add(-1);
                    result.add(-1);
                }
            }
        }
        return result;
    }

    public void save() {
        setup(true);

        try {
            trainer.getModel().save(Path.of("rl-model"), "rl-model");
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Getter
    @Setter
    @Builder
    @ToString
    private static class RatedDraw {
        private Draw draw;
        private float rating;
    }

    @Getter
    @Setter
    @Builder
    @ToString
    private static class Learning {
        private GameRound gameRound;
        private Draw draw;
        private Map<PlayerColor, Float> influence;
    }
}

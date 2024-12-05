package de.neebs.franchise.control;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.ml.linalg.Vectors;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FranchiseMLService {
    private static final Random RANDOM = new Random();

    private final FranchiseCoreService franchiseCoreService;

    private LinearRegressionModel model;

    public String play2(GameRound round, int times, boolean header) {
        StringBuilder sb = new StringBuilder();
        if (header) {
            sb.append(String.join(",", createHeader()));
            sb.append(System.lineSeparator());
        }
        for (int i = 0; i < times; i++) {
            Map<GameRound, Integer> map = play2(round);
            for (Map.Entry<GameRound, Integer> entry : map.entrySet()) {
                List<Integer> run = createVectorizedBoard(entry.getKey(), true);
                run.add(entry.getValue());
                sb.append(String.join(",", run.stream().map(String::valueOf).toList()));
                sb.append(System.lineSeparator());
            }
        }
        return new String(sb);
    }

    public Map<GameRound, Integer> play2(GameRound round) {
        List<GameRound> rounds = new ArrayList<>();
        rounds.add(round);
        while (!round.isEnd()) {
            List<Draw> draws = franchiseCoreService.nextDraws(round);
            int random;
            if (draws.size() > 1) {
                random = RANDOM.nextInt(draws.size() - 1) + 1;
            } else {
                random = 0;
            }
            Draw draw = draws.get(random);
            round = franchiseCoreService.manualDraw(round, draw).getGameRound();
            rounds.add(round);
        }
        Map<GameRound, Integer> map = new HashMap<>();
        Map<PlayerColor, Integer> score = franchiseCoreService.score(round.getScores());
        for (GameRound gr : rounds) {
            map.put(gr, score.get(gr.getActual() == null ? gr.getNext() : gr.getActual()));
        }
        return map;
    }

    public List<String> createHeader() {
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
        result.add("Score");
        return result;
    }

    private List<Integer> createVectorizedBoard(GameRound round, boolean includePlayerStats) {
        List<Integer> result = new ArrayList<>();
        result.add(round.getActual() == null ? round.getNext().ordinal() : round.getActual().ordinal());
        result.add(round.getNext().ordinal());
        for (City city : City.values()) {
            CityPlate plate = round.getPlates().get(city);
            if (plate == null) {
                plate = new CityPlate(false, new ArrayList<>());
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

    public void setupLearnings(GameRound gameRound) {
        SparkSession spark = SparkSession.builder()
                .appName("Franchise ML Example")
                .master("local[*]")
                .config("spark.driver.extraJavaOptions", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED")
                .config("spark.executor.extraJavaOptions", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --conf spark.dynamicAllocation.enabled=false")
                .config("spark.ui.enabled", "false") // Disable Spark UI
                .getOrCreate();

        String tmpDir = System.getProperty("java.io.tmpdir");

        String dataFile = tmpDir + "data.csv";

        Dataset<Row> df = spark.read()
                .format("csv")
                .option("header", "true")
                .option("inferSchema", "true")
                .load(dataFile);

        StructType schema = df.schema();
        String[] inputCols = schema.fieldNames();
        List<String> inputs = Arrays.asList(inputCols);
        inputs = new ArrayList<>(inputs);
        inputs.remove("Score");
        for (PlayerColor pc : PlayerColor.values()) {
            inputs.removeIf(f -> f.startsWith(pc.name()));
        }
        inputCols = inputs.toArray(new String[0]);

        VectorAssembler assembler = new VectorAssembler()
                .setInputCols(inputCols)
                .setOutputCol("features");

        Dataset<Row> vectorData = assembler.transform(df);

        // Create and train the model
        LinearRegression lr = new LinearRegression().setFeaturesCol("features").setLabelCol("Score");
        model = lr.train(vectorData);

        spark.close();
    }

    public Draw machineLearning(GameRound round) {
        List<Draw> draws = franchiseCoreService.nextDraws(round);
        List<RatedDraw> ratedDraws = new ArrayList<>();
        for (Draw draw : draws) {
            ExtendedGameRound gr = franchiseCoreService.manualDraw(round, draw);
            List<Integer> list = createVectorizedBoard(gr.getGameRound(), false);
            double[] doubles = list.stream().mapToDouble(f -> (double)f).toArray();
            Vector vector = Vectors.dense(doubles);
            double value = model.predict(vector);
            ratedDraws.add(RatedDraw.builder().draw(draw).rating(value).build());
        }
        ratedDraws.sort(Comparator.comparing(RatedDraw::getRating).reversed());
        int maxIndex = Math.min(ratedDraws.size(), 3);
        return ratedDraws.get(RANDOM.nextInt(maxIndex)).getDraw();
    }

    @Getter
    @Setter
    @Builder
    private static class RatedDraw {
        private Draw draw;
        private Double rating;
    }
}

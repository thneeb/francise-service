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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FranchiseMLService {
    private static final Random RANDOM = new Random();

    private final FranchiseCoreService franchiseCoreService;

    private final List<List<Integer>> unsavedTrainingData = new ArrayList<>();

    private LinearRegressionModel regressionModel;

    public void init() {
        if (regressionModel != null) {
            return;
        }
        SparkSession spark = SparkSession.builder()
                .appName("Franchise ML Example")
                .master("local[*]")
                .config("spark.driver.extraJavaOptions", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED")
                .config("spark.executor.extraJavaOptions", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --conf spark.dynamicAllocation.enabled=false")
                .config("spark.ui.enabled", "false") // Disable Spark UI
                .getOrCreate();

        String dataFile = Path.of("ml-model", "data.csv").toString();

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
        regressionModel = lr.train(vectorData);
        spark.close();
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

    public Draw machineLearning(GameRound round, int range) {
        init();

        List<Draw> draws = franchiseCoreService.nextDraws(round);
        List<RatedDraw> ratedDraws = new ArrayList<>();
        for (Draw draw : draws) {
            ExtendedGameRound gr = franchiseCoreService.manualDraw(round, draw);
            List<Integer> list = createVectorizedBoard(gr.getGameRound(), false);
            double[] doubles = list.stream().mapToDouble(f -> (double)f).toArray();
            Vector vector = Vectors.dense(doubles);
            double value = regressionModel.predict(vector);
            ratedDraws.add(RatedDraw.builder().draw(draw).rating(value).build());
        }
        ratedDraws.sort(Comparator.comparing(RatedDraw::getRating).reversed());
        int maxIndex = Math.min(ratedDraws.size(), range);
        return ratedDraws.get(RANDOM.nextInt(maxIndex)).getDraw();
    }

    public void train(List<GameRoundDraw> gameRoundDraws) {
        init();

        GameRound round = gameRoundDraws.get(gameRoundDraws.size() - 1).getGameRound();
        Map<GameRound, Integer> map = new HashMap<>();
        Map<PlayerColor, Integer> score = franchiseCoreService.score(round.getScores());
        for (GameRoundDraw gr : gameRoundDraws) {
            map.put(gr.getGameRound(), score.get(gr.getGameRound().getActual() == null ? gr.getGameRound().getNext() : gr.getGameRound().getActual()));
        }

        for (Map.Entry<GameRound, Integer> entry : map.entrySet()) {
            List<Integer> run = createVectorizedBoard(entry.getKey(), true);
            run.add(entry.getValue());
            unsavedTrainingData.add(run);
        }
    }

    public void save() {
        init();

        List<String> list = new ArrayList<>();
        for (List<Integer> data : unsavedTrainingData) {
            list.add(String.join(",", data.stream().map(String::valueOf).toList()));
        }

        try {
            Files.write(Path.of("ml-model", "data.csv"), list, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        unsavedTrainingData.clear();
    }

    @Getter
    @Setter
    @Builder
    private static class RatedDraw {
        private Draw draw;
        private Double rating;
    }
}

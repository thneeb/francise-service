package de.neebs.franchise.control;

import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class MachineLearningUtil {
    @Bean
    public LinearRegressionModel regressionModel() {
        SparkSession spark = SparkSession.builder()
                .appName("Franchise ML Example")
                .master("local[*]")
                .config("spark.driver.extraJavaOptions", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED")
                .config("spark.executor.extraJavaOptions", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --conf spark.dynamicAllocation.enabled=false")
                .config("spark.ui.enabled", "false") // Disable Spark UI
                .getOrCreate();

        String dataFile = Path.of("ml-model", "data.csv").toString();

//        String tmpDir = System.getProperty("java.io.tmpdir");
//        String dataFile = tmpDir + "data.csv";

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
        LinearRegressionModel model = lr.train(vectorData);
        spark.close();
        return model;
    }
}

package de.neebs.franchise.control;

import org.junit.jupiter.api.Test;

class NeuralNetworkTest {
    @Test
    void calculatePermutations() {
        for (int playerCount = 2; playerCount <= 6; playerCount++) {
            int count = 0;
            for (City city : City.values()) {
                int cityPermutation = 0;
                for (int i = 0; i < city.getSize(); i++) {
                    cityPermutation += Math.pow(playerCount, i + 1);
                }
                count += cityPermutation;
            }
            System.out.println(playerCount + " - " + count);
        }
    }
}

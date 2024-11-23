package de.neebs.franchise.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MoneyMap {
    private final Map<Integer, Integer> earn = new HashMap<>();
    private final List<Integer> initial = new ArrayList<>();
    private final int max;

    public MoneyMap(int max) {
        this.max = max;
    }

    public MoneyMap addEarn(int lowerScore, int upperScore, int money) {
        for (int i = lowerScore; i <= upperScore; i++) {
            earn.put(i, money);
        }
        return this;
    }

    public MoneyMap addInitial(int money) {
        initial.add(money);
        return this;
    }

    public List<Integer> getInitialMoney() {
        return initial;
    }

    public int getMoneyByScore(int score) {
        Integer money = earn.get(score);
        if (money != null) {
            return money;
        } else {
            return max;
        }
    }
}

package de.neebs.franchise.control;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@EqualsAndHashCode(exclude = {"influence"})
@ToString
public class Score {
    private int money;
    private int influence;
    private int bonusTiles;
    private int income;

    public static Score copy(Score value) {
        Score score = new Score();
        score.setBonusTiles(value.getBonusTiles());
        score.setInfluence(value.getInfluence());
        score.setMoney(value.getMoney());
        score.setIncome(value.getIncome());
        return score;
    }
}

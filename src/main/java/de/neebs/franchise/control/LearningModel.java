package de.neebs.franchise.control;

import java.util.List;

public interface LearningModel {
    void train(List<GameRoundDraw> gameRoundDraws);

    void save();
}

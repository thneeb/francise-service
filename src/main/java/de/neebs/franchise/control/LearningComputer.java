package de.neebs.franchise.control;

import java.util.List;

public interface LearningComputer {
    void init();

    void load();

    void learn(List<GameRoundDraw> grds);

    void save();
}

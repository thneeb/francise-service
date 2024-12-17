package de.neebs.franchise.control;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GameEngine {
    GameRound initGame(List<PlayerColor> players);

    ComputerPlayer createComputerPlayer(Algorithm algorithm, PlayerColor playerColor, Map<String, Object> params);

    LearningModel createLearningModel(Algorithm algorithm);

    void play(GameRound round, Set<ComputerPlayer> players, Set<LearningModel> learningModels, Map<String, Object> params, int times);

    List<GameRoundDraw> play(GameRound round, Set<ComputerPlayer> players);

    ExtendedGameRound makeDraw(GameRound round, Draw draw);

    List<Draw> nextPossibleDraws(GameRound round);
}

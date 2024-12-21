package de.neebs.franchise.control;

import lombok.*;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(exclude = {"end", "scoredRegions", "round", "scores"})
public class GameRound {
    private List<PlayerColor> players;
    private PlayerColor next;
    private PlayerColor actual;
    private int round;
    private Map<PlayerColor, Score> scores;
    private Map<City, CityPlate> plates;
    private List<Region> scoredRegions;
    private Map<Region, PlayerColor> firstCityScorers;
    private boolean end;

    public static GameRound copy(GameRound gameRound) {
        return new GameRound(
                gameRound.getPlayers(),
                gameRound.getNext(),
                gameRound.getActual(),
                gameRound.getRound(),
                gameRound.getScores().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> Score.copy(f.getValue()))),
                gameRound.getPlates().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> CityPlate.copy(f.getValue()))),
                new ArrayList<>(gameRound.getScoredRegions()),
                new HashMap<>(gameRound.getFirstCityScorers()),
                gameRound.isEnd());
    }

    public static GameRound init(GameRound gameRound) {
        GameRound next = GameRound.copy(gameRound);
        next.setRound(next.getRound() + 1);
        next.setActual(next.getNext());
        return next;
    }

    boolean isInitialization() {
        return round <= players.size();
    }

    public boolean isUpcomingRoundInitialization() {
        return round + 1 <= players.size();
    }

    public Score getActualScore() {
        return scores.get(actual);
    }

    boolean canUseBonusTile() {
        return round >= players.size() * 2 && scores.get(next).getBonusTiles() > 0;
    }

    public boolean isUpcomingRoundBonusTileUsable() {
        return round >= players.size() * 2 && scores.get(next).getBonusTiles() > 0;
    }
}

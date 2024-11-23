package de.neebs.franchise.control;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FranchiseService {
    private static final Random RANDOM = new Random();

    private final Map<GameRound, Map<Draw, List<Integer>>> learnings = new HashMap<>();

    public GameRound init(List<PlayerColor> players) {
        MoneyMap moneyMap = Rules.MONEY_MAP.get(players.size());
        Map<PlayerColor, Score> scores = new EnumMap<>(PlayerColor.class);
        for (int i = 0; i < players.size() && i < moneyMap.getInitialMoney().size(); i++) {
            Score score = new Score();
            score.setInfluence(0);
            score.setBonusTiles(players.size() == 2 ? 4 : 3);
            score.setMoney(moneyMap.getInitialMoney().get(i));
            scores.put(players.get(i), score);
        }
        Map<City, CityPlate> cityPlates = new EnumMap<>(City.class);
        List<Region> regionScores = new ArrayList<>();
        if (players.size() == 2) {
            PlayerColor neutralColor = Arrays.stream(PlayerColor.values()).filter(f -> !players.contains(f)).findAny().orElseThrow();
            CityPlate plate = new CityPlate(true, new ArrayList<>());
            plate.getBranches().add(neutralColor);
            for (Region region : Set.of(Region.CALIFORNIA, Region.UPPER_WEST, Region.MONTANA)) {
                for (City city : region.getCities()) {
                    cityPlates.put(city, plate);
                }
                regionScores.add(region);
            }
        }
        return new GameRound(
                players,
                players.get(players.size() - 1),
                null,
                0,
                scores,
                cityPlates,
                regionScores,
                new EnumMap<>(Region.class),
                false
                );
    }

    public List<GameRoundDraw> nextRounds(GameRound gameRound, int count) {
        List<GameRoundDraw> set = List.of(GameRoundDraw.builder().gameRound(new ExtendedGameRound(gameRound, null)).build());
        for (int i = 0; i < count && set.size() < 25000; i++) {
            List<GameRoundDraw> intermediate = new ArrayList<>();
            for (GameRoundDraw round : set) {
                intermediate.addAll(nextRound2(round.getGameRound().getGameRound()));
            }
            set = intermediate;
            log.info("Round " + (i + 1) + ": " + set.size());
        }
        for (GameRoundDraw round : set) {
            scoreRound(round.getGameRound().getGameRound(), null);
        }
        log.info("Scored: " + set.size());
        return set;
    }

    private List<GameRoundDraw> nextRound2(GameRound gameRound) {
        List<GameRoundDraw> rounds = new ArrayList<>();
        List<Draw> draws = nextDraws(gameRound);
        for (Draw draw : draws) {
            rounds.add(GameRoundDraw.builder().draw(draw).gameRound(manualDraw(gameRound, draw)).build());
        }
        return rounds;
    }

    public void play(GameRound round, int times, boolean useLearnings) {
        int knownStates = 0;
        for (int i = 0; i < times; i++) {
            int oldLearnings = learnings.size();
            long millis = System.currentTimeMillis();
            List<GameRoundDraw> list = play(round, useLearnings);
            log.info("Iteration: " + i + ", Learnings: " + learnings.size() + ", Game length: " + list.size() + ", Learning Diff: " + (learnings.size() - oldLearnings) + ", known states: " + (list.size() + oldLearnings - learnings.size()) + ", elapsed time: " + (System.currentTimeMillis() - millis));
            knownStates += (list.size() + oldLearnings - learnings.size());
        }
        log.info("Average known states: " + knownStates / times);
    }

    public List<GameRoundDraw> play(GameRound round, boolean useLearnings) {
        List<GameRoundDraw> list = new ArrayList<>();
        while (!round.isEnd()) {
            List<Draw> draws = nextDraws(round);
            Draw draw;
            int random;
            if (draws.size() > 1) {
                random = RANDOM.nextInt(draws.size() - 1) + 1;
            } else {
                random = 0;
            }
            if (useLearnings) {
                draw = filterDrawsByLearnings(round, draws).orElse(draws.get(random));
            } else {
                draw = draws.get(random);
            }
            list.add(GameRoundDraw.builder().gameRound(new ExtendedGameRound(round, null)).draw(draw).build());
            round = manualDraw(round, draw).getGameRound();
        }
        Map<PlayerColor, Integer> score = score(round.getScores());
        for (GameRoundDraw grd : list) {
            learn(grd.getGameRound().getGameRound(), grd.getDraw(), score);
        }
        return list;
    }

    public Optional<Draw> filterDrawsByLearnings(GameRound round, List<Draw> draws) {
        Map<Draw, List<Integer>> map = learnings.get(round);
        if (map == null) {
            return Optional.empty();
        }
        List<Draw> bestDraws = draws.stream()
                .filter(f -> map.get(f) != null)
                .collect(Collectors.toMap(Function.identity(), f -> map.get(f).stream().mapToInt(g -> g).average().orElseThrow()))
                .entrySet().stream().sorted((o1, o2) -> -Double.compare(o1.getValue(), o2.getValue()))
                .map(Map.Entry::getKey).toList();
        if (bestDraws.isEmpty()) {
            return Optional.empty();
        }
        if (RANDOM.nextDouble(1) < 0.9) {
            log.info("Hit " + round.getRound());
            return Optional.of(bestDraws.get(RANDOM.nextInt(Math.min(bestDraws.size(), 6))));
        } else {
            return Optional.empty();
        }
    }

    private void learn(GameRound gameRound, Draw draw, Map<PlayerColor, Integer> playerScores) {
        Map<Draw, List<Integer>> map = learnings.computeIfAbsent(gameRound, k -> new HashMap<>());
        List<Integer> list = map.computeIfAbsent(draw, k -> new ArrayList<>());
        list.add(playerScores.get(gameRound.getNext()));
    }

    private Set<City> retrieveOwnedCities(Map<City, CityPlate> plates, PlayerColor playerColor) {
        return plates.entrySet().stream().filter(f -> f.getValue().getBranches().contains(playerColor)).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public List<Draw> nextDraws(GameRound gameRound) {
        if (gameRound.isEnd()) {
            return List.of();
        } else if (gameRound.getRound() + 1 <= gameRound.getPlayers().size()) {
            return City.getTowns().stream().filter(f -> !gameRound.getPlates().containsKey(f)).map(f -> Draw.builder().extension(Set.of(f)).increase(List.of()).build()).toList();
        } else {
            int income = calcIncome(gameRound, gameRound.getNext());
            int money = gameRound.getScores().get(gameRound.getNext()).getMoney() + income;
            // evaluate owned cities
            Set<City> owned = retrieveOwnedCities(gameRound.getPlates(), gameRound.getNext());
            // ne extension
            Map<Draw, Integer> map = new HashMap<>();
            map.put(Draw.builder().extension(Set.of()).increase(List.of()).build(), money);
            if (canUseBonusTile(gameRound)) {
                map.put(Draw.builder().extension(Set.of()).increase(List.of()).bonusTileUsage(BonusTileUsage.MONEY).build(), money + 10);
            }
            // extension to one city in the neighbourhood
            Map<Draw, Integer> extension = evaluateExtensions(gameRound, owned, map);
            // is a second extension possible?
            if (canUseBonusTile(gameRound)) {
                map.putAll(evaluateExtensions(gameRound, owned, extension));
            }
            map.putAll(extension);
            // increases
            return increase(gameRound, owned, map);
        }
    }

    private List<Draw> increase(GameRound gameRound, Set<City> owned, Map<Draw, Integer> drawMoneyMap) {
        List<Draw> list = new ArrayList<>();
        for (Map.Entry<Draw, Integer> entry : drawMoneyMap.entrySet()) {
            Set<Draw> inner = new HashSet<>();
            inner.add(entry.getKey());
            for (City city : gameRound.getPlates().entrySet().stream().filter(f -> owned.contains(f.getKey())).filter(f -> !f.getValue().isClosed()).map(Map.Entry::getKey).toList()) {
                Set<Draw> increases = increaseForCity(gameRound, entry, inner, city);
                inner.addAll(increases);
            }
            list.addAll(inner);
        }
        return list;
    }

    private Set<Draw> increaseForCity(GameRound gameRound, Map.Entry<Draw, Integer> entry, Set<Draw> inner, City city) {
        Set<Draw> increases = new HashSet<>();
        for (Draw draw : inner) {
            if (draw.getIncrease().size() < entry.getValue()) {
                List<City> cities = new ArrayList<>(draw.getIncrease());
                cities.add(city);
                increases.add(Draw.builder().extension(draw.getExtension()).increase(cities).bonusTileUsage(draw.getBonusTileUsage()).build());
                if (canUseBonusTile(gameRound) && !draw.isBonusTile()) {
                    CityPlate cityPlate = gameRound.getPlates().get(city);
                    if (cityPlate.getBranches().size() + 2 > city.getSize()) {
                         continue; // not enough places left
                    }
                    if (cities.size() >= entry.getValue()) {
                        continue; // we do not have enough money to pay it
                    }
                    if (cityPlate.getBranches().stream().filter(f -> gameRound.getNext() == f).count() + 1 > city.getSize() / 2) {
                        continue; // the first plate will already close it
                    }
                    cities = new ArrayList<>(cities);
                    cities.add(city);
                    increases.add(Draw.builder().extension(draw.getExtension()).increase(cities).bonusTileUsage(BonusTileUsage.INCREASE).build());
                }
            }
        }
        return increases;
    }

    private boolean canUseBonusTile(GameRound gameRound) {
        return gameRound.getRound() >= gameRound.getPlayers().size() * 2 && gameRound.getScores().get(gameRound.getNext()).getBonusTiles() > 0;
    }

    private Map<Draw, Integer> evaluateExtensions(GameRound gameRound, Set<City> owned, Map<Draw, Integer> basis) {
        Map<Draw, Integer> map = new HashMap<>();
        for (Map.Entry<Draw, Integer> entry : basis.entrySet()) {
            for (City city : Arrays.stream(City.values()).filter(f -> !owned.contains(f)).toList()) {
                Optional<Connection> optionalConnection = Rules.CONNECTIONS.stream()
                        .filter(f -> f.getCities().contains(city) && f.getCities().stream().anyMatch(owned::contains))
                        .min(Comparator.comparingInt(Connection::getCosts));
                if (optionalConnection.isPresent()
                        && entry.getValue() >= optionalConnection.get().getCosts()
                        && (gameRound.getPlates().get(city) == null || !gameRound.getPlates().get(city).isClosed())
                        && !entry.getKey().getExtension().contains(city)
                        && (!entry.getKey().isBonusTile() || entry.getKey().getExtension().isEmpty())) {
                    Set<City> set = new HashSet<>(entry.getKey().getExtension());
                    set.add(city);
                    map.put(Draw.builder().extension(set).increase(List.of()).bonusTileUsage(set.size() > 1 ? BonusTileUsage.EXTENSION : entry.getKey().getBonusTileUsage()).build(), entry.getValue() - optionalConnection.get().getCosts());
                }
            }
        }
        return map;
    }

    private void nextPlayer(GameRound next) {
        int index = next.getPlayers().indexOf(next.getNext());
        if (next.isInitialization()) {
            if (index > 0) {
                next.setNext(next.getPlayers().get(index - 1));
            }
        } else {
            index++;
            next.setNext(next.getPlayers().get(index % next.getPlayers().size()));
        }
    }

    void scoreRegions(GameRound gameRound, AdditionalInfo additionalInfo) {
        for (Region region : Arrays.stream(Region.values()).filter(f -> !gameRound.getScoredRegions().contains(f)).toList()) {
            boolean scoreIt = true;
            for (City city : region.getCities()) {
                CityPlate plate = gameRound.getPlates().get(city);
                if (plate == null || !plate.isClosed()) {
                    scoreIt = false;
                }
            }
            if (scoreIt) {
                scoreRegion(gameRound, region, additionalInfo);
            }
        }
        gameRound.setEnd(gameRound.getScoredRegions().size() > Region.values().length - 3);
        if (gameRound.isEnd()) {
            scoreRound(gameRound, additionalInfo);
        }
    }

    private void scoreRegion(GameRound gameRound, Region region, AdditionalInfo additionalInfo) {
        Map<PlayerColor, Long> counts = gameRound.getPlates().entrySet().stream()
                .filter(f -> region.getCities().contains(f.getKey()))
                .flatMap(f -> f.getValue().getBranches().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<Long, Set<PlayerColor>> map = counts.entrySet().stream().collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));
        List<Set<PlayerColor>> list = map.entrySet().stream().sorted((o1, o2) -> Long.compare(o2.getKey(), o1.getKey())).map(Map.Entry::getValue).toList();
        int profitLevel = 1;
        for (Set<PlayerColor> set : list) {
            List<PlayerColor> players = orderPlayersForRegionScoring(set, gameRound.getFirstCityScorers().get(region), gameRound.getPlayers());
            for (PlayerColor player : players) {
                int influence = region.getByProfitLevel(profitLevel);
                Score score = gameRound.getScores().get(player);
                score.setInfluence(score.getInfluence() + influence);
                if (gameRound.getPlayers().size() == 2) {
                    profitLevel += 2;
                } else {
                    profitLevel++;
                }
                if (additionalInfo != null) {
                    additionalInfo.getInfluenceComments().add("Scoring region " + region + " influence for " + player + ": " + influence);
                }
            }
        }
        Score score = gameRound.getActualScore();
        int influence = Region.getRegionFinishInfluence().get(gameRound.getScoredRegions().size());
        score.setInfluence(score.getInfluence() + influence);
        if (additionalInfo != null) {
            additionalInfo.getInfluenceComments().add("Scoring region " + region + " closed for " + gameRound.getNext() + ": " + influence);
        }
        gameRound.getScoredRegions().add(region);
    }

    private List<PlayerColor> orderPlayersForRegionScoring(Set<PlayerColor> set, PlayerColor closer, List<PlayerColor> players) {
        if (set.size() == 1) {
            return List.of(set.iterator().next());
        }
        List<PlayerColor> list = new ArrayList<>();
        for (int i = players.indexOf(closer); i < players.size(); i++) {
            if (set.contains(players.get(i))) {
                list.add(players.get(i));
            }
        }
        for (int i = 0; i <= players.indexOf(closer) - 1; i++) {
            if (set.contains(players.get(i))) {
                list.add(players.get(i));
            }
        }
        return list;
    }

    private void scoreCities(GameRound round, AdditionalInfo additionalInfo) {
        Score score = round.getScores().get(round.getNext());
        for (Map.Entry<City, CityPlate> entry : round.getPlates().entrySet()) {
            if (entry.getKey().getSize() == 1 && !entry.getValue().isClosed()) {
                entry.getValue().setClosed(true);
            }
            if (entry.getValue().isClosed()) {
                continue;
            }

            Map<PlayerColor, Long> map = entry.getValue().getBranches().stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            long max = map.values().stream().max(Comparator.naturalOrder()).orElse(0L);
            Set<PlayerColor> bestPlayers = map.entrySet().stream().filter(f -> f.getValue() == max).map(Map.Entry::getKey).collect(Collectors.toSet());
            PlayerColor playerColor = null;
            Integer influence = null;
            if (max > entry.getKey().getSize() / 2) {
                playerColor = bestPlayers.iterator().next();
                influence = entry.getKey().getSize();
            } else if (entry.getValue().getBranches().size() == entry.getKey().getSize()) {
                playerColor = entry.getValue().getBranches().stream().filter(bestPlayers::contains).findFirst().orElseThrow();
                influence = entry.getKey().getSize() / 2;
            }
            if (playerColor != null) {
                score.setInfluence(score.getInfluence() + influence);
                if (additionalInfo != null) {
                    additionalInfo.getInfluenceComments().add("Scoring city " + entry.getKey() + " most influence for " + playerColor + ": " + influence);
                }
                entry.getValue().getBranches().removeIf(f -> f == round.getNext());
                entry.getValue().getBranches().add(round.getNext());
                entry.getValue().setClosed(true);
                Region region = Arrays.stream(Region.values()).filter(f -> f.getCities().contains(entry.getKey())).findAny().orElseThrow();
                round.getFirstCityScorers().computeIfAbsent(region, k -> round.getNext());
            }
        }
    }

    private void openFranchise(GameRound round) {
        Score score = round.getScores().get(round.getNext());
        for (City city : score.getExpansions()) {
            CityPlate plate = round.getPlates().get(city);
            if (plate == null) {
                plate = new CityPlate(false, new ArrayList<>());
                round.getPlates().put(city, plate);
            }
            plate.getBranches().add(round.getNext());
        }
        score.getExpansions().clear();
    }

    private void expand(GameRound gameRound, City city, AdditionalInfo additionalInfo) {
        CityPlate optional = gameRound.getPlates().get(city);
        if (optional != null) {
            // if city is already close, we cannot expand to there
            if (optional.isClosed()) {
                throw new IllegalStateException();
            }
            // if the player has already a branch here
            if (optional.getBranches().contains(gameRound.getNext())) {
                throw new IllegalStateException();
            }
        }
        Set<City> owned = gameRound.getPlates().entrySet().stream().filter(f -> f.getValue().getBranches().contains(gameRound.getActual())).map(Map.Entry::getKey).collect(Collectors.toSet());
        Optional<Connection> optionalConnection = Rules.CONNECTIONS.stream()
                .filter(f -> f.getCities().contains(city) && f.getCities().stream().anyMatch(owned::contains))
                .min(Comparator.comparingInt(Connection::getCosts));
        if (optionalConnection.isPresent() && (gameRound.getScores().get(gameRound.getNext()).getMoney() >= optionalConnection.get().getCosts())) {
            Score score = gameRound.getScores().get(gameRound.getNext());
            score.getExpansions().add(city);
            score.setMoney(score.getMoney() - optionalConnection.get().getCosts());
            if (additionalInfo != null) {
                additionalInfo.getInfluenceComments().add("Extension costs for " + city + ": "+ optionalConnection.get().getCosts());
            }
        } else {
            throw new IllegalStateException();
        }
    }

    public int calcIncome(GameRound gameRound, PlayerColor playerColor) {
        return Rules.MONEY_MAP.get(gameRound.getPlayers().size()).getMoneyByScore(calcIncomeScore(playerColor, gameRound.getPlates()));
    }

    private int calcIncomeScore(PlayerColor playerColor, Map<City, CityPlate> plates) {
        int moneyScore = 0;
        for (Map.Entry<City, CityPlate> entry : plates.entrySet().stream().filter(f -> !f.getValue().isClosed() && f.getValue().getBranches().contains(playerColor)).collect(Collectors.toSet())) {
            moneyScore += entry.getKey().getSize() - entry.getValue().getBranches().size();
        }
        return moneyScore;
    }

    public void scoreRound(GameRound round, AdditionalInfo additionalInfo) {
        scoreTowns(round, additionalInfo);
        scoreMoney(round, additionalInfo, 3);
        scoreBonusTiles(round, additionalInfo, 4);
    }

    private void scoreBonusTiles(GameRound round, AdditionalInfo additionalInfo, int value) {
        for (Map.Entry<PlayerColor, Score> entry : round.getScores().entrySet()) {
            Score score = entry.getValue();

            int influence = score.getBonusTiles() * value;
            score.setInfluence(score.getInfluence() + influence);
            score.setBonusTiles(0);
            if (additionalInfo != null) {
                additionalInfo.getInfluenceComments().add("Final score bonus tiles for " + entry.getKey() + ": " + influence);
            }
        }
    }

    private void scoreMoney(GameRound round, AdditionalInfo additionalInfo, int divisor) {
        for (Map.Entry<PlayerColor, Score> entry : round.getScores().entrySet()) {
            Score score = entry.getValue();

            int influence = score.getMoney() / divisor;
            score.setInfluence(score.getInfluence() + influence);
            score.setMoney(score.getMoney() % divisor);
            if (additionalInfo != null) {
                additionalInfo.getInfluenceComments().add("Final score money for " + entry.getKey() + ": " + influence);
            }
        }
    }

    int openPlates(GameRound round) {
        return Arrays.stream(City.values())
                .filter(f -> round.getPlayers().size() > 2 || round.getPlates().get(f) == null || round.getPlates().get(f).getBranches().get(0) != PlayerColor.BLACK)
                .filter(f -> round.getPlates().get(f) == null || !round.getPlates().get(f).isClosed())
                .mapToInt(f -> f.getSize() - (round.getPlates().get(f) == null ? 0 : round.getPlates().get(f).getBranches().size()))
                .sum();
    }

    /**
     * Evaluates the game phase by calculating the percentage of the open branch positions devided through the possible
     * branches
     * @param round a game round
     * @return percentage (0-100)
     */
    GamePhase evaluateGamePhase(GameRound round) {
        int openPlates = openPlates(round) * 100 / (round.getPlayers().size() == 2 ? 94 : 121);
        if (openPlates <= 30) {
            return GamePhase.END;
        } else if (openPlates >= 75) {
            return GamePhase.START;
        } else {
            return GamePhase.GROW;
        }
    }

    private void scoreIncome(GameRound round, int divisor) {
        for (Map.Entry<PlayerColor, Score> entry : round.getScores().entrySet()) {
            Score score = entry.getValue();
            int influence = (calcIncomeScore(entry.getKey(), round.getPlates())) / divisor;
            score.setInfluence(score.getInfluence() + influence);
        }
    }

    private void scoreTowns(GameRound round, AdditionalInfo additionalInfo) {
        Map<PlayerColor, Long> towns = round.getPlates().entrySet().stream()
                .filter(f -> f.getKey().getSize() == 1)
                .filter(f -> round.getPlayers().contains(f.getValue().getBranches().get(0)))
                .map(f -> f.getValue().getBranches().get(0))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        for (Map.Entry<PlayerColor, Long> entry : towns.entrySet()) {
            Score score = round.getScores().get(entry.getKey());
            int influence = entry.getValue().intValue();
            score.setInfluence(score.getInfluence() + influence);
            if (additionalInfo != null) {
                additionalInfo.getInfluenceComments().add("Final score towns for " + entry.getKey() + ": " + influence);
            }
        }
    }

    public Draw minimax(List<GameRoundDraw> rounds, PlayerColor playerColor) {
        Map<GameRoundDraw, Map<PlayerColor, Integer>> scores = score(rounds);
        Map<GameRoundDraw, GameRoundDraw> bestMoves = new HashMap<>();
        while (scores.size() > 1) {
            Map<GameRoundDraw, Optional<Map.Entry<GameRoundDraw, Map<PlayerColor, Integer>>>> scores2;
            if (scores.entrySet().iterator().next().getKey().getGameRound().getGameRound().getActual() == playerColor) {
                scores2 = scores.entrySet().stream().collect(
                        Collectors.groupingBy(f -> f.getKey().getPredecessor(),
                                Collectors.reducing((a, b) -> a.getValue().get(playerColor) > b.getValue().get(playerColor) ? a : b)));
            } else {
                scores2 = scores.entrySet().stream().collect(
                        Collectors.groupingBy(f -> f.getKey().getPredecessor(),
                                Collectors.reducing((a, b) -> a.getValue().get(playerColor) < b.getValue().get(playerColor) ? a : b)));
            }
            scores = scores2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().orElseThrow().getValue()));
            bestMoves = scores2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().orElseThrow().getKey()));
        }
        return bestMoves.values().iterator().next().getDraw();
    }

    public Draw findBestMove(List<GameRoundDraw> rounds) {
        Map<GameRoundDraw, Map<PlayerColor, Integer>> scores = score(rounds);
        Map<GameRoundDraw, GameRoundDraw> bestMoves = new HashMap<>();
        while (scores.size() > 1) {
            Map<GameRoundDraw, Optional<Map.Entry<GameRoundDraw, Map<PlayerColor, Integer>>>> scores2 = scores.entrySet().stream()
                    .collect(
                            Collectors.groupingBy(f -> f.getKey().getPredecessor(),
                                    Collectors.reducing((a, b) -> a.getValue().get(a.getKey().getGameRound().getGameRound().getActual()) > b.getValue().get(b.getKey().getGameRound().getGameRound().getActual()) ? a : b)));
            scores = scores2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().orElseThrow().getValue()));
            bestMoves = scores2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().orElseThrow().getKey()));
        }
        return bestMoves.values().iterator().next().getDraw();
    }

    private Map<GameRoundDraw, Map<PlayerColor, Integer>> score(List<GameRoundDraw> rounds) {
        Map<GameRoundDraw, Map<PlayerColor, Integer>> map = new HashMap<>();
        for (GameRoundDraw round : rounds) {
            Map<PlayerColor, Integer> scores = score(round.getGameRound().getGameRound().getScores());
            map.put(round, scores);
        }
        return map;
    }

    private Map<PlayerColor, Integer> score(Map<PlayerColor, Score> s) {
        return calculateInfluenceDifferences(s.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().getInfluence())));
    }

    Map<PlayerColor, Integer> calculateInfluenceDifferences(Map<PlayerColor, Integer> influences) {
        Map<PlayerColor, Integer> scores = new EnumMap<>(PlayerColor.class);
        for (Map.Entry<PlayerColor, Integer> entry : influences.entrySet()) {
            int o1MyInfluence = entry.getValue();
            int o1SecondInfluence = influences.entrySet().stream()
                    .filter(f -> !f.getKey().equals(entry.getKey()))
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .orElseThrow().getValue();
            scores.put(entry.getKey(), o1MyInfluence - o1SecondInfluence);
        }
        return scores;
    }

    public Draw computerDraw(GameRound gameRound) {
        List<Draw> draws = nextDraws(gameRound);
        return filterDrawsByLearnings(gameRound, draws).orElse(draws.get(RANDOM.nextInt(draws.size())));
    }

    public ExtendedGameRound manualDraw(GameRound oldGameRound, Draw draw) {
        GameRound gameRound = GameRound.init(oldGameRound);
        AdditionalInfo additionalInfo = new AdditionalInfo();
        additionalInfo.setInfluenceComments(new ArrayList<>());
        if (gameRound.isInitialization()) {
            manualDrawInitialization(gameRound, draw.getExtension());
        } else {
            manualDrawStandard(gameRound, draw.getExtension(), draw.getIncrease(), draw.getBonusTileUsage(), additionalInfo);
        }
        nextPlayer(gameRound);
        return new ExtendedGameRound(gameRound, additionalInfo);
    }

    private void manualDrawStandard(GameRound gameRound, Set<City> extension, List<City> increase, BonusTileUsage bonusTile, AdditionalInfo additionalInfo) {
        Map<City, Long> counts = isDrawAllowed(gameRound, extension, increase, bonusTile);
        Score score = gameRound.getActualScore();
        int income = calcIncome(gameRound, gameRound.getActual());
        if (bonusTile == BonusTileUsage.MONEY) {
            income += 10;
        }
        if (additionalInfo != null) {
            additionalInfo.setMoney(score.getMoney());
            additionalInfo.setIncome(income);
        }
        score.setMoney(score.getMoney() + income);
        if (bonusTile != null) {
            score.setBonusTiles(score.getBonusTiles() - 1);
        }

        for (City city : extension) {
            expand(gameRound, city, additionalInfo);
        }
        for (Map.Entry<City, Long> entry : counts.entrySet()) {
            CityPlate plate = gameRound.getPlates().get(entry.getKey());
            if (!plate.isClosed() // not already closed
                    && plate.getBranches().contains(gameRound.getNext()) // have an own branch in th ecity
                    && score.getMoney() >= entry.getValue() // have enough value for building the branch(es)
                    && plate.getBranches().size() + entry.getValue() <= entry.getKey().getSize()) { // enough empty spaces for the branches
                for (int i = 0; i < entry.getValue(); i++) {
                    plate.getBranches().add(gameRound.getNext());
                }
                score.setMoney(score.getMoney() - entry.getValue().intValue());
            } else {
                throw new IllegalArgumentException("Cannot increase market share in " + entry.getKey().getName());
            }
        }
        openFranchise(gameRound);
        scoreCities(gameRound, additionalInfo);
        scoreRegions(gameRound, additionalInfo);
    }

    private Map<City, Long> isDrawAllowed(GameRound gameRound, Set<City> extension, List<City> increase, BonusTileUsage bonusTile) {
        if (bonusTile != null && gameRound.getActualScore().getBonusTiles() == 0) {
            throw new IllegalArgumentException("All bonus tiles are already used");
        }
        if (extension.size() == 1 && bonusTile == BonusTileUsage.EXTENSION) {
            throw new IllegalArgumentException("No bonus tile needed for expansion to one city");
        }
        if (extension.size() == 2 && bonusTile != BonusTileUsage.EXTENSION) {
            throw new IllegalArgumentException("Expansion to two cities is only allowed with bonus tile");
        }
        if (extension.size() > 2) {
            throw new IllegalArgumentException("Expansion to more then two cities is not allowed");
        }
        Map<City, Long> counts = increase.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        if (counts.values().stream().noneMatch(f -> f > 1) && bonusTile == BonusTileUsage.INCREASE) {
            throw new IllegalArgumentException("No bonus tile need for one increase per city");
        }
        if (counts.values().stream().filter(f -> f > 1).count() == 1 && bonusTile != BonusTileUsage.INCREASE) {
            throw new IllegalArgumentException("Increasing one city more then once per round is only allowed when using a bonus tile");
        }
        if (counts.values().stream().anyMatch(f -> f > 2)) {
            throw new IllegalArgumentException("Increasing one city more then twice is always prohibited");
        }
        return counts;
    }

    private void manualDrawInitialization(GameRound gameRound, Set<City> extension) {
        if (extension.size() != 1) {
            throw new IllegalArgumentException("In initialization phase exactly one extension is needed");
        }
        City city = extension.iterator().next();
        if (!City.getTowns().contains(city)) {
            throw new IllegalArgumentException("In initialization phase only towns are allowed");
        }
        CityPlate plate = gameRound.getPlates().get(city);
        if (plate != null) {
            throw new IllegalArgumentException("Can only use towns, which are not occupied so far");
        }
        plate = new CityPlate(true, new ArrayList<>());
        plate.getBranches().add(gameRound.getNext());
        gameRound.getPlates().put(city, plate);
    }

    private List<Draw> filterAndSortDraws(GameRound round, List<Draw> draws) {
        GamePhase phase = evaluateGamePhase(round);
        if (phase == GamePhase.START) {
            draws = draws.stream()
                    .filter(f -> f.getBonusTileUsage() != BonusTileUsage.INCREASE)
                    .filter(f -> !f.getExtension().isEmpty())
                    .toList();
        }
        return draws.stream().sorted((o1, o2) -> Boolean.compare(o2.isBonusTile(), o1.isBonusTile())).toList();
    }

    public ScoredDraw minimaxAbPrune(GameRound round, int deep) {
        Map<PlayerColor, Integer> alpha = new HashMap<>();
        Map<PlayerColor, Integer> beta = new HashMap<>();
        for (PlayerColor color : round.getPlayers()) {
            alpha.put(color, -10000);
            beta.put(color, 10000);
        }
        return minimaxAbPrune2(round, round.getNext(), deep, alpha, beta, new HashMap<>(), null);
    }

    public ScoredDraw minimaxAbPrune2(GameRound round, PlayerColor actual, int deep, Map<PlayerColor, Integer> alpha, Map<PlayerColor, Integer> beta, Map<Integer, Integer> savedScores, List<ScoredDraw> scoredDraws) {
        if (deep == 0 || round.isEnd()) {
            int score = evaluatePosition(round, actual);
            return ScoredDraw.builder().gameRound(round).score(score).build();
        }

        int extremeScore = round.getNext() == actual ? -10000 : +10000;
        ScoredDraw bestMove = null;
        for (Draw draw : filterAndSortDraws(round, nextDraws(round))) {
            GameRound newBoard = manualDraw(round, draw).getGameRound();
            if (scoredDraws != null) {
                log.info("Analyzing draw: " + draw);
            }

            final ScoredDraw scoredDraw;
            if (savedScores.get(newBoard.hashCode()) == null) {
                scoredDraw = minimaxAbPrune2(newBoard, actual, deep - 1, alpha, beta, savedScores, null);
                if (newBoard.getActual() == actual) {
                    if (scoredDraw.getScore() > extremeScore) {
                        extremeScore = scoredDraw.getScore();
                        bestMove = scoredDraw;
                    }
                    if (scoredDraw.getScore() > alpha.get(newBoard.getActual())) {
                        alpha.put(newBoard.getActual(), scoredDraw.getScore());
                    }
                } else {
                    if (scoredDraw.getScore() < extremeScore) {
                        extremeScore = scoredDraw.getScore();
                        bestMove = scoredDraw;
                    }
                    if (scoredDraw.getScore() < beta.get(newBoard.getActual())) {
                        beta.put(newBoard.getActual(), scoredDraw.getScore());
                    }
                }
                savedScores.put(newBoard.hashCode(), scoredDraw.getScore());
            } else {
                scoredDraw = ScoredDraw.builder().score(savedScores.get(newBoard.hashCode())).build();
            }
            scoredDraw.setDraw(draw);
            if (scoredDraws != null) {
                scoredDraws.add(scoredDraw);
            }
            if (alpha.get(newBoard.getActual()) > beta.get(newBoard.getActual())) {
                break;
            }
        }
        if (bestMove == null) {
            return ScoredDraw.builder().score(extremeScore).build();
        } else {
            return bestMove;
        }
    }


    /*
    def alphabeta(game_state, depth, alpha, beta, player_index):
    if depth == 0 or game_state is terminal:
        return evaluate(game_state, player_index)

    # Get legal moves for the current player
    legal_moves = get_legal_moves(game_state, player_index)

    if player_index is the maximizing player:
        max_eval = -infinity
        for move in legal_moves:
            # Make the move and call the function recursively
            new_state = make_move(game_state, move, player_index)
            eval = alphabeta(new_state, depth - 1, alpha, beta, (player_index + 1) % num_players)
            max_eval = max(max_eval, eval)
            alpha[player_index] = max(alpha[player_index], eval)
            if alpha[player_index] >= beta[player_index]:
                break  # Prune branch
        return max_eval
    else:
        # Player is a minimizing player
        min_eval = infinity
        for move in legal_moves:
            new_state = make_move(game_state, move, player_index)
            eval = alphabeta(new_state, depth - 1, alpha, beta, (player_index + 1) % num_players)
            min_eval = min(min_eval, eval)
            beta[player_index] = min(beta[player_index], eval)
            if alpha[player_index] >= beta[player_index]:
                break  # Prune branch
        return min_eval
     */
    public ScoredDraw minimaxAbPrune(GameRound round, PlayerColor actual, int deep, int alpha, int beta, Map<Integer, Integer> savedScores, List<ScoredDraw> scoredDraws) {
        if (deep == 0 || round.isEnd()) {
            int score = evaluatePosition(round, actual);
//            score = round.getNext() == actual ? -score : score;
            return ScoredDraw.builder().gameRound(round).score(score).build();
        }

        if (round.getRound() <= round.getPlayers().size() * 2) {
            alpha = -10000;
            beta = 10000;
        }

        ScoredDraw bestMove = null;
        for (Draw draw : filterAndSortDraws(round, nextDraws(round))) {
            GameRound newBoard = manualDraw(round, draw).getGameRound();
            if (scoredDraws != null) {
                log.info("Analyzing draw: " + draw);
            }
            Integer score = savedScores.get(newBoard.hashCode());
            ScoredDraw scoredDraw;
            if (score == null) {
                if ((newBoard.getActual() == actual || newBoard.getNext() == actual) && newBoard.getActual() != newBoard.getNext()) {
                    scoredDraw = minimaxAbPrune(newBoard, actual, deep - 1, -beta, -alpha, savedScores, null);
                    score = -scoredDraw.getScore();
                } else {
                    scoredDraw = minimaxAbPrune(newBoard, actual, deep - 1, alpha, beta, savedScores, null);
                    score = scoredDraw.getScore();
                }
                savedScores.put(newBoard.hashCode(), score);
            } else {
                scoredDraw = ScoredDraw.builder().score(score).gameRound(newBoard).build();
            }
            scoredDraw.setScore(score);
            scoredDraw.setDraw(draw);
            if (scoredDraws != null) {
                scoredDraws.add(scoredDraw);
            }
            if (score > alpha) {
                alpha = score;
                bestMove = scoredDraw;
                if (alpha >= beta) {
                    break;
                }
            }
        }
        if (bestMove == null) {
            return ScoredDraw.builder().score(alpha).build();
        } else {
            return bestMove;
        }
    }

    private int evaluatePosition(GameRound round, PlayerColor color) {
        GamePhase phase = evaluateGamePhase(round);
        scoreTowns(round, null);
        scoreMoney(round, null, switch (phase) {
            case START -> 6;
            case GROW, END -> 3;
        });
        scoreBonusTiles(round, null, switch (phase) {
            case START -> 1;
            case GROW -> 2;
            case END -> 4;
        });
        scoreIncome(round, switch (phase) {
            case START -> 2;
            case GROW -> 4;
            case END -> 5;
        });
        scoreRegionPossession(round);
        return score(round.getScores()).get(color);
    }

    private void scoreRegionPossession(GameRound round) {
        for (PlayerColor playerColor : round.getPlayers()) {
            Set<City> ownedCities = retrieveOwnedCities(round.getPlates(), playerColor);
            int possessedRegions = (int)Arrays.stream(Region.values())
                    .filter(f -> ownedCities.stream().anyMatch(g -> f.getCities().contains(g))).count();
            Score score = round.getScores().get(playerColor);
            score.setInfluence(score.getInfluence() + possessedRegions * 1);
        }
    }

    public Draw divideAndConquer(GameRound round, int deep, int slice) {
        List<ScoredDraw> scoredDraws = new ArrayList<>();
        minimaxAbPrune(round, round.getNext(), deep, -10000, +10000, new HashMap<>(), scoredDraws);
        scoredDraws.sort((o1, o2) -> -Integer.compare(o1.getScore(), o2.getScore()));
        if (slice == 0) {
            return scoredDraws.get(0).getDraw();
        } else {
            ScoredDraw result = null;
            for (ScoredDraw sd : scoredDraws.stream().filter(f -> f.getGameRound() != null).limit(Math.min(scoredDraws.size(), slice)).toList()) {
                log.info("Analyzing below: " + sd);
                ScoredDraw draw = minimaxAbPrune(sd.getGameRound(), sd.getGameRound().getNext(), deep, -10000, +10000, new HashMap<>(), new ArrayList<>());
                if (result == null
                        || (result.getScore() < draw.getScore() && deep % round.getPlayers().size() == 0)
                        || (result.getScore() > draw.getScore() && deep % round.getPlayers().size() > 0)) {
                    result = sd;
                }
            }
            assert result != null;
            log.info(result.toString());
            return result.getDraw();
        }
    }
}

package de.neebs.franchise.control;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FranchiseCoreService {
    public GameRound init(List<PlayerColor> players) {
        Map<PlayerColor, Score> scores = Rules.initScores(players);
        Map<City, CityPlate> cityPlates = new EnumMap<>(City.class);
        List<Region> regionScores = new ArrayList<>();
        PlayerColor neutralColor = Arrays.stream(PlayerColor.values()).filter(f -> !players.contains(f)).findAny().orElseThrow();
        CityPlate blockedPlate = new CityPlate(true, List.of(neutralColor), null);
        CityPlate emptyPlate = new CityPlate(false, List.of(), null);

        for (City city : City.values()) {
            if (players.size() == 2 && Set.of(Region.CALIFORNIA, Region.UPPER_WEST, Region.MONTANA).stream().flatMap(f -> f.getCities().stream()).anyMatch(f -> f == city)) {
                cityPlates.put(city, blockedPlate);
            } else {
                cityPlates.put(city, emptyPlate);
            }
        }
        if (players.size() == 2) {
            regionScores.addAll(Set.of(Region.CALIFORNIA, Region.UPPER_WEST, Region.MONTANA));
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

    private void nextPlayer(GameRound next) {
        int index = next.getPlayers().indexOf(next.getNext());
        if (next.isInitialization()) {
            if (index > 0) {
                next.setNext(next.getPlayers().get(index - 1));
            } else {
                updateExtensionCosts(next);
            }
        } else {
            index++;
            next.setNext(next.getPlayers().get(index % next.getPlayers().size()));
            updateExtensionCosts(next);
        }
    }

    private void updateExtensionCosts(GameRound round) {
        Set<City> ownCities = retrieveOwnedCities(round.getPlates(), round.getNext());
        for (City city : City.values()) {
            CityPlate plate = round.getPlates().get(city);
            if (plate != null) {
                plate.setExtensionCosts(null);
                if (!ownCities.contains(city) && !plate.isClosed() && !plate.getBranches().contains(round.getNext())) {
                        Optional<Connection> optionalConnection = Rules.CONNECTIONS.stream()
                                .filter(f -> f.getCities().contains(city) && f.getCities().stream().anyMatch(ownCities::contains))
                                .min(Comparator.comparingInt(Connection::getCosts));
                    optionalConnection.ifPresent(connection -> plate.setExtensionCosts(connection.getCosts()));
                }
            }
        }
    }

    private void manualDrawStandard(GameRound gameRound, Set<City> extension, List<City> increase, BonusTileUsage bonusTile, AdditionalInfo additionalInfo) {
        Map<City, Long> counts = isDrawAllowed(gameRound, extension, increase, bonusTile);
        Score score = gameRound.getActualScore();
        score.setIncome(calcIncome(gameRound, gameRound.getActual()));
        score.setMoney(score.getMoney() + score.getIncome());
        if (bonusTile != null) {
            score.setBonusTiles(score.getBonusTiles() - 1);
        }
        if (bonusTile == BonusTileUsage.MONEY) {
            score.setMoney(score.getMoney() + 10);
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
                throw new IllegalDrawException("Cannot increase market share in " + entry.getKey().getName());
            }
        }
        openFranchise(gameRound);
        scoreCities(gameRound, additionalInfo);
        scoreRegions(gameRound, additionalInfo);
        if (additionalInfo != null) {
            additionalInfo.setMoney(score.getMoney());
            additionalInfo.setIncome(calcIncome(gameRound, gameRound.getActual()));
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

    public void scoreRound(GameRound round, AdditionalInfo additionalInfo) {
        scoreTowns(round, additionalInfo);
        scoreMoney(round, additionalInfo, 3);
        scoreBonusTiles(round, additionalInfo, 4);
    }

    void scoreTowns(GameRound round, AdditionalInfo additionalInfo) {
        Map<PlayerColor, Long> towns = round.getPlates().entrySet().stream()
                .filter(f -> f.getKey().getSize() == 1)
                .filter(f -> !f.getValue().getBranches().isEmpty())
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

    void scoreBonusTiles(GameRound round, AdditionalInfo additionalInfo, int value) {
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

    void scoreMoney(GameRound round, AdditionalInfo additionalInfo, int divisor) {
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
            if (entry.getKey().getSize() == 1 && entry.getValue().getBranches().size() == 1 && !entry.getValue().isClosed()) {
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
                plate = new CityPlate(false, new ArrayList<>(), null);
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
                throw new IllegalDrawException("City " + city + " is already closed");
            }
            // if the player has already a branch here
            if (optional.getBranches().contains(gameRound.getNext())) {
                throw new IllegalDrawException("Player " + gameRound.getNext() + " already has a branch in " + city);
            }

            if (optional.getExtensionCosts() == null) {
                throw new IllegalDrawException("No connection exists for " + city);
            }

            Score score = gameRound.getScores().get(gameRound.getNext());
            if (score.getMoney() < optional.getExtensionCosts()) {
                throw new IllegalDrawException("Not enough money for expansion to " + city);
            }

            score.getExpansions().add(city);
            score.setMoney(score.getMoney() - optional.getExtensionCosts());
            if (additionalInfo != null) {
                additionalInfo.getInfluenceComments().add("Extension costs for " + city + ": "+ optional.getExtensionCosts());
            }
        }
    }

    private Map<City, Long> isDrawAllowed(GameRound gameRound, Set<City> extension, List<City> increase, BonusTileUsage bonusTile) {
        if (bonusTile != null && gameRound.getActualScore().getBonusTiles() == 0) {
            throw new IllegalDrawException("All bonus tiles are already used");
        }
        if (extension.size() == 1 && bonusTile == BonusTileUsage.EXTENSION) {
            throw new IllegalDrawException("No bonus tile needed for expansion to one city");
        }
        if (extension.size() == 2 && bonusTile != BonusTileUsage.EXTENSION) {
            throw new IllegalDrawException("Expansion to two cities is only allowed with bonus tile");
        }
        if (extension.size() > 2) {
            throw new IllegalDrawException("Expansion to more then two cities is not allowed");
        }
        Map<City, Long> counts = increase.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        if (counts.values().stream().noneMatch(f -> f > 1) && bonusTile == BonusTileUsage.INCREASE) {
            throw new IllegalDrawException("No bonus tile need for one increase per city");
        }
        if (counts.values().stream().filter(f -> f > 1).count() == 1 && bonusTile != BonusTileUsage.INCREASE) {
            throw new IllegalDrawException("Increasing one city more then once per round is only allowed when using a bonus tile");
        }
        if (counts.values().stream().anyMatch(f -> f > 2)) {
            throw new IllegalDrawException("Increasing one city more then twice is always prohibited");
        }
        return counts;
    }

    private void manualDrawInitialization(GameRound gameRound, Set<City> extension) {
        if (extension.size() != 1) {
            throw new IllegalDrawException("In initialization phase exactly one extension is needed");
        }
        City city = extension.iterator().next();
        if (!City.getTowns().contains(city)) {
            throw new IllegalDrawException("In initialization phase only towns are allowed");
        }
        CityPlate plate = gameRound.getPlates().get(city);
        if (plate != null && !plate.getBranches().isEmpty()) {
            throw new IllegalDrawException("Can only use towns, which are not occupied so far");
        }
        plate = new CityPlate(true, new ArrayList<>(), null);
        plate.getBranches().add(gameRound.getNext());
        gameRound.getPlates().put(city, plate);
    }

    public List<Draw> nextDraws(GameRound gameRound) {
        if (gameRound.isEnd()) {
            return List.of();
        } else if (gameRound.getRound() + 1 <= gameRound.getPlayers().size()) {
            return City.getTowns().stream()
                    .filter(f -> !gameRound.getPlates().containsKey(f) || gameRound.getPlates().get(f).getBranches().isEmpty())
                    .map(f -> Draw.builder().extension(Set.of(f)).increase(List.of()).build()).toList();
        } else {
            int income = calcIncome(gameRound, gameRound.getNext());
            int money = gameRound.getScores().get(gameRound.getNext()).getMoney() + income;
            // evaluate owned cities
            Set<City> owned = retrieveOwnedCities(gameRound.getPlates(), gameRound.getNext());
            // ne extension
            Map<Draw, Integer> map = new HashMap<>();
            map.put(Draw.builder().extension(Set.of()).increase(List.of()).build(), money);
            if (gameRound.canUseBonusTile()) {
                map.put(Draw.builder().extension(Set.of()).increase(List.of()).bonusTileUsage(BonusTileUsage.MONEY).build(), money + 10);
            }
            // extension to one city in the neighbourhood
            Map<Draw, Integer> extension = evaluateExtensions(gameRound, owned, map);
            // is a second extension possible?
            if (gameRound.canUseBonusTile()) {
                map.putAll(evaluateExtensions(gameRound, owned, extension));
            }
            map.putAll(extension);
            // increases
            return increase(gameRound, owned, map);
        }
    }

    int calcIncome(GameRound gameRound, PlayerColor playerColor) {
        return Rules.calcIncome(gameRound.getPlayers().size(), calcIncomeScore(playerColor, gameRound.getPlates()));
    }

    int calcIncomeScore(PlayerColor playerColor, Map<City, CityPlate> plates) {
        int moneyScore = 0;
        for (Map.Entry<City, CityPlate> entry : plates.entrySet().stream().filter(f -> !f.getValue().isClosed() && f.getValue().getBranches().contains(playerColor)).collect(Collectors.toSet())) {
            moneyScore += entry.getKey().getSize() - entry.getValue().getBranches().size();
        }
        return moneyScore;
    }

    Set<City> retrieveOwnedCities(Map<City, CityPlate> plates, PlayerColor playerColor) {
        return plates.entrySet().stream().filter(f -> f.getValue().getBranches().contains(playerColor)).map(Map.Entry::getKey).collect(Collectors.toSet());
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
                if (gameRound.canUseBonusTile() && !draw.isBonusTile()) {
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

    private Map<Draw, Integer> evaluateExtensions(GameRound gameRound, Set<City> owned, Map<Draw, Integer> basis) {
        Map<Draw, Integer> map = new HashMap<>();
        for (Map.Entry<Draw, Integer> entry : basis.entrySet()) {
            for (City city : Arrays.stream(City.values()).filter(f -> !owned.contains(f)).toList()) {
                Integer costs = gameRound.getPlates().get(city).getExtensionCosts();
                if (costs != null // es gibt eine Möglichkeit zu expandieren
                        && entry.getValue() >= costs // die Kosten können getragen werden
                        && !entry.getKey().getExtension().contains(city) // die Stadt ist noch nicht im Draw
                        && (!entry.getKey().isBonusTile() || entry.getKey().getExtension().isEmpty())) { // es wurde noch kein Bonus Tile verwendet oder es wurde noch keine Stadt expandiert
                    Set<City> set = new HashSet<>(entry.getKey().getExtension());
                    set.add(city);
                    map.put(Draw.builder().extension(set).increase(List.of()).bonusTileUsage(set.size() > 1 ? BonusTileUsage.EXTENSION : entry.getKey().getBonusTileUsage()).build(), entry.getValue() - costs);
                }
            }
        }
        return map;
    }


    Map<PlayerColor, Integer> score(Map<PlayerColor, Score> s) {
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
}

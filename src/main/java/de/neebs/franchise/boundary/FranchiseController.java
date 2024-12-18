/*
 * Copyright (C) 2023 Tele Columbus AG. All rights reserved.
 * This file and its contents are the sole property of Tele Columbus AG.
 */
package de.neebs.franchise.boundary;

import de.neebs.franchise.control.*;
import de.neebs.franchise.client.boundary.DefaultApi;
import de.neebs.franchise.client.entity.*;
import de.neebs.franchise.client.entity.BonusTileUsage;
import de.neebs.franchise.client.entity.City;
import de.neebs.franchise.client.entity.CityPlate;
import de.neebs.franchise.client.entity.Draw;
import de.neebs.franchise.client.entity.PlayerColor;
import de.neebs.franchise.client.entity.Region;
import de.neebs.franchise.control.ComputerPlayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FranchiseController implements DefaultApi {
    private final Map<String, GameRound> games = new HashMap<>();

    private final Map<String, List<GameRoundDraw>> gameRoundDraws = new HashMap<>();

    private final GameEngine gameEngine;
    private final Map<String, Set<ComputerStrategy>> learningAlgorithms = new HashMap<>();

    @Override
    public ResponseEntity<GameField> initializeGame(GameConfig gameConfig) {
        List<de.neebs.franchise.control.PlayerColor> players = gameConfig.getPlayers().stream().map(this::mapPlayerColor).toList();
        Set<ComputerStrategy> algorithms = gameConfig.getLearningModels() == null ? Set.of() : new HashSet<>(gameConfig.getLearningModels());
        GameRound round = gameEngine.initGame(players);
        String uuid = UUID.randomUUID().toString();
        games.put(uuid, round);
        learningAlgorithms.put(uuid, algorithms);
        String href = linkTo(methodOn(getClass()).retrieveGameBoard(uuid)).withSelfRel().getHref();
        GameField field = mapGameField(round);
        return ResponseEntity.created(URI.create(href)).body(field);
    }

    @Override
    public ResponseEntity<GameField> retrieveGameBoard(String gameId) {
        GameRound round = games.get(gameId);
        GameField field = mapGameField(round);
        return ResponseEntity.ok(field);
    }

    private @NotNull GameField mapGameField(GameRound round) {
        GameField field = new GameField();
        field.setEnd(round.isEnd());
        field.setFirstCities(round.getFirstCityScorers().entrySet().stream()
                .map(f -> PlayerRegion.builder().region(mapRegion(f.getKey())).color(mapPlayerColor(f.getValue())).build()).toList());
        field.setClosedRegions(round.getScoredRegions().stream().map(this::mapRegion).toList());
        field.setNext(mapPlayerColor(round.getNext()));
        field.setCities(round.getPlates().entrySet().stream()
                .map(f -> CityPlate.builder()
                        .city(City.valueOf(f.getKey().name()))
                        .size(f.getKey().getSize())
                        .closed(f.getValue().isClosed())
                        .branches(f.getValue().getBranches().stream().map(this::mapPlayerColor).toList())
                        .build()).toList());
        field.setPlayers(round.getScores().entrySet().stream()
                .map(f -> Player.builder()
                        .color(mapPlayerColor(f.getKey()))
                        .bonusTiles(f.getValue().getBonusTiles())
                        .money(f.getValue().getMoney())
                        .income(f.getValue().getIncome())
                        .influence(f.getValue().getInfluence())
                        .build()).toList());
        return field;
    }

    @Override
    public ResponseEntity<ExtendedDraw> createDraw(String gameId, Draw draw) {
        GameRound round = games.get(gameId);
        if (mapPlayerColor(round.getNext()) != draw.getColor()) {
            throw new IllegalDrawException("Not your turn");
        }
        if (round.isEnd()) {
            throw new IllegalDrawException("Game is already over");
        }
        final HumanDraw humanDraw;
        if (draw.getPlayerType() == PlayerType.COMPUTER) {
            final de.neebs.franchise.client.entity.ComputerPlayer computer = (de.neebs.franchise.client.entity.ComputerPlayer) draw;
            final ComputerPlayer player = createComputerPlayer(computer);
            humanDraw = mapDraw(mapPlayerColor(round.getNext()), player.evaluateDraw(round));
        } else {
            humanDraw = (HumanDraw) draw;
        }
        log.info("Best Move: " + draw);

        gameRoundDraws.computeIfAbsent(gameId, k -> new ArrayList<>()).add(GameRoundDraw.builder().gameRound(round).draw(mapDraw(humanDraw)).build());

        ExtendedGameRound extendedGameRound = gameEngine.makeDraw(round, mapDraw(humanDraw));

        if (extendedGameRound.getGameRound().isEnd()) {
            for (ComputerStrategy strategy : learningAlgorithms.get(gameId)) {
                LearningModel model = gameEngine.createLearningModel(mapAlgorithm(strategy));
                model.train(gameRoundDraws.get(gameId));
                model.save();
            }
        }

        games.put(gameId, extendedGameRound.getGameRound());
        ExtendedDraw extendedDraw = new ExtendedDraw();
        extendedDraw.setDraw(humanDraw);
        if (extendedGameRound.getAdditionalInfo() != null) {
            extendedDraw.setInfo(new ExtendedDrawInfo());
            extendedDraw.getInfo().setIncome(extendedGameRound.getAdditionalInfo().getIncome());
            extendedDraw.getInfo().setInfluence(extendedGameRound.getAdditionalInfo().getInfluenceComments());
        }
        return ResponseEntity.ok(extendedDraw);
    }

    @Override
    public ResponseEntity<List<HumanDraw>> evaluateNextPossibleDraws(String gameId) {
        GameRound round = games.get(gameId);
        return ResponseEntity.ok(gameEngine.nextPossibleDraws(round).stream().map(f -> mapDraw(mapPlayerColor(round.getNext()), f)).toList());
    }

    @Override
    public ResponseEntity<List<PlayerColorAndInteger>> playGame(String gameId, PlayConfig playConfig) {
        GameRound round = games.get(gameId);
        if (playConfig == null) {
            throw new IllegalArgumentException("No config given");
        }
        int times = playConfig.getTimesToPlay() == null ? 1 : playConfig.getTimesToPlay();
        Set<ComputerPlayer> players = playConfig.getPlayers().stream().map(this::createComputerPlayer).collect(Collectors.toSet());
        Set<LearningModel> learningModels = playConfig.getLearningModels() == null ? Set.of() : playConfig.getLearningModels().stream().map(this::createLearningModel).collect(Collectors.toSet());
        return ResponseEntity.ok(mapResult(gameEngine.play(round, players, learningModels, playConfig.getParams(), times)));
    }

    private Algorithm mapAlgorithm(ComputerStrategy strategy) {
        return Algorithm.valueOf(strategy.name());
    }

    private ComputerPlayer createComputerPlayer(de.neebs.franchise.client.entity.ComputerPlayer computer) {
        return gameEngine.createComputerPlayer(mapAlgorithm(computer.getStrategy()), mapPlayerColor(computer.getColor()), computer.getParams());
    }

    private LearningModel createLearningModel(ComputerStrategy strategy) {
        return gameEngine.createLearningModel(mapAlgorithm(strategy));
    }

    private de.neebs.franchise.control.Draw mapDraw(HumanDraw draw) {
        return de.neebs.franchise.control.Draw.builder()
                .extension(draw.getExtension() == null ? new HashSet<>() : draw.getExtension().stream().map(this::mapCity).collect(Collectors.toSet()))
                .increase(draw.getIncrease() == null ? new ArrayList<>() : draw.getIncrease().stream().map(this::mapCity).toList())
                .bonusTileUsage(draw.getBonusTileUsage() == null ? null : de.neebs.franchise.control.BonusTileUsage.valueOf(draw.getBonusTileUsage().getValue()))
                .build();
    }

    private HumanDraw mapDraw(PlayerColor playerColor, de.neebs.franchise.control.Draw f) {
        return HumanDraw.builder()
                .color(playerColor)
                .extension(f.getExtension().stream().map(this::mapCity).toList())
                .increase(f.getIncrease().stream().map(this::mapCity).toList())
                .bonusTileUsage(f.getBonusTileUsage() == null ? null : BonusTileUsage.valueOf(f.getBonusTileUsage().name()))
                .build();
    }

    private de.neebs.franchise.control.City mapCity(City city) {
        return Arrays.stream(de.neebs.franchise.control.City.values()).filter(f -> f.name().equalsIgnoreCase(city.name())).findAny().orElseThrow();
    }

    private City mapCity(de.neebs.franchise.control.City city) {
        return City.valueOf(city.name());
    }

    private Region mapRegion(de.neebs.franchise.control.Region region) {
        return Region.valueOf(region.name());
    }

    private PlayerColor mapPlayerColor(de.neebs.franchise.control.PlayerColor f) {
        return PlayerColor.valueOf(f.name());
    }

    private de.neebs.franchise.control.PlayerColor mapPlayerColor(PlayerColor f) {
        return de.neebs.franchise.control.PlayerColor.valueOf(f.name());
    }

    private List<PlayerColorAndInteger> mapResult(Map<de.neebs.franchise.control.PlayerColor, Integer> results) {
        return results.entrySet().stream().map(f ->
                PlayerColorAndInteger.builder().color(mapPlayerColor(f.getKey())).value(f.getValue()).build()).toList();
    }
}

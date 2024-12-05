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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static de.neebs.franchise.control.PlayerColor.BLUE;
import static de.neebs.franchise.control.PlayerColor.RED;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FranchiseController implements DefaultApi {
    private final Map<String, GameRound> games = new HashMap<>();

    private final FranchiseService franchiseService;

    private final FranchiseMLService franchiseMLService;

    private final FranchiseCoreService franchiseCoreService;

    @Override
    public ResponseEntity<Void> initializeGame() {
        GameRound gameRound = franchiseService.init(List.of(BLUE, RED));
        String uuid = UUID.randomUUID().toString();
        games.put(uuid, gameRound);
        String href = linkTo(methodOn(getClass()).retrieveGameBoard(uuid)).withSelfRel().getHref();
        return ResponseEntity.created(URI.create(href)).build();
    }

    @Override
    public ResponseEntity<GameField> retrieveGameBoard(String gameId) {
        GameRound round = games.get(gameId);
        GameField field = new GameField();
        field.setEnd(round.isEnd());
        field.setFirstCities(round.getFirstCityScorers().entrySet().stream()
                .map(f -> PlayerRegion.builder().region(mapRegion(f.getKey())).color(mapPlayerColor(f.getValue())).build()).toList());
        field.setClosedRegions(round.getScoredRegions().stream().map(this::mapRegion).toList());
        field.setNext(mapPlayerColor(round.getNext()));
        field.setCities(round.getPlates().entrySet().stream()
                .map(f -> CityPlate.builder()
                        .city(City.builder().name(f.getKey().getName()).build())
                        .size(f.getKey().getSize())
                        .closed(f.getValue().isClosed())
                        .branches(f.getValue().getBranches().stream().map(this::mapPlayerColor).toList())
                        .build()).toList());
        field.setPlayers(round.getScores().entrySet().stream()
                .map(f -> Player.builder()
                        .color(mapPlayerColor(f.getKey()))
                        .bonusTiles(f.getValue().getBonusTiles())
                        .money(f.getValue().getMoney())
                        .income(franchiseCoreService.calcIncome(round, f.getKey()))
                        .influence(f.getValue().getInfluence())
                        .build()).toList());
        return ResponseEntity.ok(field);
    }

    @Override
    public ResponseEntity<ExtendedDraw> createDraw(String gameId, Draw draw) {
        GameRound round = games.get(gameId);
        if (mapPlayerColor(round.getNext()) != draw.getColor()) {
            return ResponseEntity.badRequest().build();
        }
        if (round.isEnd()) {
            return ResponseEntity.badRequest().build();
        }
        if (draw.getComputer() != null) {
            Computer computer = draw.getComputer();
            if (computer.getStrategy() == ComputerStrategy.LEARNINGS) {
                draw = mapDraw(franchiseService.computerDraw(round));
            } else if (computer.getStrategy() == ComputerStrategy.BEST_MOVE) {
                int deep = computer.getDeep() == null ? 3 : computer.getDeep();
                List<GameRoundDraw> rounds = franchiseService.nextRounds(round, deep);
                draw = mapDraw(franchiseService.findBestMove(rounds));
            } else if (computer.getStrategy() == ComputerStrategy.MINIMAX) {
                int deep = computer.getDeep() == null ? 3 : computer.getDeep();
                List<GameRoundDraw> rounds = franchiseService.nextRounds(round, deep);
                draw = mapDraw(franchiseService.minimax(rounds, round.getNext()));
            } else if (computer.getStrategy() == ComputerStrategy.AB_PRUNE) {
                int deep = computer.getDeep() == null ? 3 : computer.getDeep();
                draw = mapDraw(franchiseService.minimaxAbPrune(round, deep).getDraw());
            } else if (computer.getStrategy() == ComputerStrategy.DIVIDE_AND_CONQUER) {
                int deep = computer.getDeep() == null ? 3 : computer.getDeep();
                int slice = computer.getSlice() == null ? 3 : computer.getDeep();
                draw = mapDraw(franchiseService.divideAndConquer(round, deep, slice));
            } else if (computer.getStrategy() == ComputerStrategy.MACHINE_LEARNING) {
                draw = mapDraw(franchiseMLService.machineLearning(round));
            } else {
                throw new IllegalArgumentException("Unknown strategy");
            }
        }
        log.info("Best Move: " + draw);

        ExtendedGameRound extendedGameRound = franchiseCoreService.manualDraw(round, mapDraw(draw));

        games.put(gameId, extendedGameRound.getGameRound());
        ExtendedDraw extendedDraw = new ExtendedDraw();
        extendedDraw.setDraw(draw);
        if (extendedGameRound.getAdditionalInfo() != null) {
            extendedDraw.setInfo(new ExtendedDrawInfo());
            extendedDraw.getInfo().setIncome(extendedGameRound.getAdditionalInfo().getIncome());
            extendedDraw.getInfo().setInfluence(extendedGameRound.getAdditionalInfo().getInfluenceComments());
        }
        return ResponseEntity.ok(extendedDraw);
    }

    @Override
    public ResponseEntity<List<Draw>> evaluateNextPossibleDraws(String gameId) {
        GameRound round = games.get(gameId);
        return ResponseEntity.ok(franchiseCoreService.nextDraws(round).stream().map(this::mapDraw).toList());
    }

    @Override
    public ResponseEntity<List<ExtendedDraw>> playGame(String gameId, PlayConfig playConfig) {
        GameRound round = games.get(gameId);
        int times = playConfig == null ? 1 : playConfig.getTimesToPlay() == null ? 1 : playConfig.getTimesToPlay();
        boolean useLearnings = playConfig != null && playConfig.getUseLearnings() != null && playConfig.getUseLearnings();
        franchiseService.play(round, times, useLearnings);
        return ResponseEntity.ok(new ArrayList<>());
    }

    @Override
    public ResponseEntity<String> learnGame(String gameId, PlayConfig playConfig) {
        GameRound round = games.get(gameId);
        int times = playConfig == null || playConfig.getTimesToPlay() == null ? 1 : playConfig.getTimesToPlay();
        boolean header = playConfig != null && playConfig.getHeader() != null && playConfig.getHeader();
        return ResponseEntity.ok(franchiseMLService.play2(round, times, header));
    }

    @Override
    public ResponseEntity<Void> setupLearnings(String gameId) {
        franchiseMLService.setupLearnings(games.get(gameId));
        return ResponseEntity.ok().build();
    }

    private de.neebs.franchise.control.Draw mapDraw(Draw draw) {
        return de.neebs.franchise.control.Draw.builder()
                .extension(draw.getExtension() == null ? new HashSet<>() : draw.getExtension().stream().map(this::mapCity).collect(Collectors.toSet()))
                .increase(draw.getIncrease() == null ? new ArrayList<>() : draw.getIncrease().stream().map(this::mapCity).toList())
                .bonusTileUsage(draw.getBonusTileUsage() == null ? null : de.neebs.franchise.control.BonusTileUsage.valueOf(draw.getBonusTileUsage().getValue()))
                .build();
    }

    private Draw mapDraw(de.neebs.franchise.control.Draw f) {
        return Draw.builder()
                .extension(f.getExtension().stream().map(this::mapCity).toList())
                .increase(f.getIncrease().stream().map(this::mapCity).toList())
                .bonusTileUsage(f.getBonusTileUsage() == null ? null : BonusTileUsage.valueOf(f.getBonusTileUsage().name()))
                .build();
    }

    private de.neebs.franchise.control.City mapCity(City city) {
        return Arrays.stream(de.neebs.franchise.control.City.values()).filter(f -> f.getName().equalsIgnoreCase(city.getName())).findAny().orElseThrow();
    }

    private City mapCity(de.neebs.franchise.control.City city) {
        return City.builder().name(city.getName()).build();
    }

    private Region mapRegion(de.neebs.franchise.control.Region region) {
        return switch (region) {
            case TEXAS -> Region.TEXAS;
            case CALIFORNIA -> Region.CALIFORNIA;
            case CENTRAL -> Region.CENTRAL;
            case MONTANA -> Region.MONTANA;
            case UPPER_WEST -> Region.UPPER_WEST;
            case FLORIDA -> Region.FLORIDA;
            case GRAND_CANYON -> Region.GRAND_CANYON;
            case GREAT_LAKES -> Region.GREAT_LAKES;
            case NEW_YORK -> Region.NEW_YORK;
            case WASHINGTON -> Region.WASHINGTON;
        };
    }

    private PlayerColor mapPlayerColor(de.neebs.franchise.control.PlayerColor f) {
        return switch (f) {
            case RED -> PlayerColor.RED;
            case BLUE -> PlayerColor.BLUE;
            case BLACK -> PlayerColor.BLACK;
            case ORANGE -> PlayerColor.ORANGE;
            case WHITE -> PlayerColor.WHITE;
        };
    }
}

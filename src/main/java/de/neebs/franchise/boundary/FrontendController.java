package de.neebs.franchise.boundary;

import de.neebs.franchise.boundary.frontend.ComputerConfig;
import de.neebs.franchise.boundary.frontend.Draw;
import de.neebs.franchise.boundary.frontend.SetupGame;
import de.neebs.franchise.client.entity.*;
import de.neebs.franchise.integration.FrontendBackendBridge;
import feign.FeignException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class FrontendController {
    private final FrontendBackendBridge frontendBackendBridge;

    @GetMapping("/")
    public String index(Model model) {
        return setupGame(new SetupGame(), model);
    }

    @GetMapping("/game-setup")
    public String setupGame(@ModelAttribute SetupGame setupGame, Model model) {
        setupGame.setAvailablePlayerColors(Arrays.asList(PlayerColor.values()));
        setupGame.setSelectedPlayerColors(Arrays.stream(PlayerColor.values()).map(PlayerColor::name).toList());
        model.addAttribute(setupGame);
        return "gamesetup";
    }

    @PostMapping("/game-setup")
    public String initializeGame(@ModelAttribute SetupGame setupGame, Model model) {
        setupGame.getSelectedPlayerColors().removeIf(f -> f == null || f.isEmpty());
        if (setupGame.getSelectedPlayerColors().stream().distinct().count() != setupGame.getSelectedPlayerColors().size()) {
            setupGame.setAvailablePlayerColors(Arrays.asList(PlayerColor.values()));
            model.addAttribute(setupGame);
            model.addAttribute("error", "Please select different colors for each player.");
            return "gamesetup";
        }

        log.info("Selected player colors: " + setupGame.getSelectedPlayerColors());
        ResponseEntity<GameField> response = frontendBackendBridge.initializeGame(GameConfig.builder()
                        .players(setupGame.getSelectedPlayerColors().stream().map(PlayerColor::valueOf).toList())
                .build());
        if (response.getHeaders().getLocation() == null) {
            throw new IllegalArgumentException();
        }
        String[] s = response.getHeaders().getLocation().getPath().split("/");
        String gameId = s[s.length - 1];
        return "redirect:/game?gameId=" + gameId;
    }

    @GetMapping("/game")
    public String displayGame(@RequestParam("gameId") String gameId, @ModelAttribute Draw draw, @ModelAttribute ComputerConfig computerConfig, Model model) {
        model.addAttribute("gameId", gameId);
        ResponseEntity<GameField> responseGameField = frontendBackendBridge.retrieveGameBoard(gameId);
        if (responseGameField.getBody() == null) {
            throw new IllegalArgumentException();
        }
        model.addAttribute(responseGameField.getBody());
        if (draw == null) {
            draw = new Draw();
        }
        model.addAttribute(draw);
        try {
            ResponseEntity<HumanDraw> responseHumanDraw = frontendBackendBridge.retrieveDraw(gameId, -1);
            model.addAttribute("lastDraw", responseHumanDraw.getBody());
        } catch (FeignException.NotFound e) {
            log.info("No human draw found");
        }
        model.addAttribute("availableBonusTiles", BonusTileUsage.values());
        model.addAttribute("availableStrategies", ComputerStrategy.values());
        if (computerConfig == null) {
            computerConfig = new ComputerConfig();
        }
        model.addAttribute(computerConfig);
        return "gamefield";
    }

    @PostMapping("/human-draw")
    public String humanDraw(@RequestParam("gameId") String gameId, @RequestParam PlayerColor color, @ModelAttribute Draw draw, Model model) {
        HumanDraw humanDraw = HumanDraw.builder()
                .color(color)
                .playerType(PlayerType.HUMAN)
                .extension(draw.getExtensions())
                .increase(draw.getIncreases().entrySet().stream()
                        .map(f -> Collections.nCopies(f.getValue(), f.getKey()))
                        .flatMap(Collection::stream)
                        .toList())
                .bonusTileUsage(draw.getBonusTileUsage())
                .build();
        ResponseEntity<ExtendedDraw> response = frontendBackendBridge.createDraw(gameId, humanDraw);
        return "redirect:/game?gameId=" + gameId;
    }

    @PostMapping("/computer-draw")
    public String computerDraw(@RequestParam("gameId") String gameId, @RequestParam PlayerColor color, @ModelAttribute ComputerConfig config, Model model) {
        ComputerPlayer computerPlayer = ComputerPlayer.builder()
                .color(color)
                .playerType(PlayerType.COMPUTER)
                .strategy(config.getStrategy())
                .params(config.getParameters())
                .build();
        ResponseEntity<ExtendedDraw> response = frontendBackendBridge.createDraw(gameId, computerPlayer);
        return "redirect:/game?gameId=" + gameId;
    }
}

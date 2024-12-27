package de.neebs.franchise.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GamePersistence {
    private final ObjectMapper objectMapper;

    private final Map<String, List<GameRoundDraw>> gameRoundDraws = new HashMap<>();

    public void saveGame(String gameId, List<GameRoundDraw> gameRoundDraw) {
        gameRoundDraws.put(gameId, gameRoundDraw);
        try {
            Files.write(Path.of("games", gameId + ".json"), objectMapper.writeValueAsBytes(gameRoundDraw), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public List<GameRoundDraw> loadGame(String gameId) {
        List<GameRoundDraw> gameRoundDraw = gameRoundDraws.get(gameId);
        if (gameRoundDraw == null) {
            try {
                String s = Files.readString(Path.of("games", gameId + ".json"));
                gameRoundDraw = objectMapper.readValue(s, new TypeReference<List<GameRoundDraw>>() {});
                gameRoundDraws.put(gameId, gameRoundDraw);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return gameRoundDraw;
    }
}

package com.codeoftheweb.salvo.Controllers;

import com.codeoftheweb.salvo.Models.*;
import com.codeoftheweb.salvo.Repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SalvoController {

    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GamePlayerRepository gamePlayerRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ShipRepository shipRepository;
    @Autowired
    private SalvoRepository salvoRepository;
    @Autowired
    private ScoreRepository scoreRepository;


    @RequestMapping("/games")
    public Map<String, Object> getAll(Authentication authentication) {
        Map<String, Object> dto = new LinkedHashMap<>();

        if (isGuest(authentication)) {
            dto.put("player", "Guest");
        } else {
            Player player = playerRepository.findByUserName(authentication.getName());
            dto.put("player", player.makePlayerDTO());
        }
        dto.put("games", gameRepository.findAll()
                .stream()
                .map(game -> game.makeGameDTO())
                .collect(Collectors.toList()));
        return dto;
    }


    @RequestMapping(path = "/games", method = RequestMethod.POST)
    public ResponseEntity<Object> createGame(Authentication authentication) {

        if (isGuest(authentication)) {
            return new ResponseEntity<>(makeMap("error", "No esta loggeado"), HttpStatus.UNAUTHORIZED);
        } else {
            Player player = playerRepository.findByUserName(authentication.getName());

            Game game = gameRepository.save(new Game());
            GamePlayer gamePlayer = gamePlayerRepository.save(new GamePlayer(player, game));

            return new ResponseEntity<>(makeMap("gpid", gamePlayer.getId()), HttpStatus.CREATED);
        }
    }

    @RequestMapping(path = "/game/{id}/players", method = RequestMethod.POST)
    public ResponseEntity<Object> joinGame(Authentication authentication, @PathVariable Long id) {

        if (isGuest(authentication)) {
            return new ResponseEntity<>(makeMap("error", "No esta loggeado"), HttpStatus.UNAUTHORIZED);
        }
        Player player = playerRepository.findByUserName(authentication.getName());
        Game game = gameRepository.findById(id).get();
        if (game == null) {
            return new ResponseEntity<>(makeMap("error", "No existe el juego"), HttpStatus.FORBIDDEN);
        }
        if (game.getGamePlayers().size() == 2) {
            return new ResponseEntity<>(makeMap("error", "Game is full"), HttpStatus.FORBIDDEN);
        }

        GamePlayer gamePlayer = gamePlayerRepository.save(new GamePlayer(player, game));
        return new ResponseEntity<>(makeMap("gpid", gamePlayer.getId()), HttpStatus.CREATED);
    }

    @RequestMapping(path = "/games/players/{gamePlayerId}/ships", method = RequestMethod.POST)
    public ResponseEntity<Object> placeShips(Authentication authentication, @PathVariable Long gamePlayerId, @RequestBody Set<Ship> ships) {

        if (isGuest(authentication)) {
            return new ResponseEntity<>(makeMap("error", "No esta loggeado"), HttpStatus.UNAUTHORIZED);
        }
        Player player = playerRepository.findByUserName(authentication.getName());
        if (player == null)
            return new ResponseEntity<>(makeMap("error", "No existe el player para ese game"), HttpStatus.UNAUTHORIZED);

        GamePlayer gamePlayer = gamePlayerRepository.findById(gamePlayerId).get();

        if (gamePlayer == null) {
            return new ResponseEntity<>(makeMap("error", "No existe el gameplayer"), HttpStatus.UNAUTHORIZED);
        }
        if (!gamePlayer.getShips().isEmpty()) {
            return new ResponseEntity<>(makeMap("error", "El usuario ya ubico sus barcos"), HttpStatus.FORBIDDEN);
        }

        ships.forEach(
                ship ->
                        shipRepository.save(new Ship(gamePlayer, ship.getType(), ship.getLocations()))

        );

        return new ResponseEntity<>(makeMap("OK", "You placed your ships"), HttpStatus.CREATED);



    }


    @RequestMapping(path = "/games/players/{gamePlayerId}/salvoes", method = RequestMethod.POST)
    public ResponseEntity<Object> placeSalvos(Authentication authentication, @PathVariable Long gamePlayerId, @RequestBody Salvo salvo) {

        if (isGuest(authentication)) {
            return new ResponseEntity<>(makeMap("error", "No esta loggeado"), HttpStatus.UNAUTHORIZED);
        }
        Player player = playerRepository.findByUserName(authentication.getName());
        if (player == null)
            return new ResponseEntity<>(makeMap("error", "No existe el player para ese game"), HttpStatus.UNAUTHORIZED);

        GamePlayer gamePlayer = gamePlayerRepository.findById(gamePlayerId).get();

        if (gamePlayer == null) {
            return new ResponseEntity<>(makeMap("error", "No existe el gameplayer"), HttpStatus.UNAUTHORIZED);
        }

        salvoRepository.save(new Salvo(gamePlayer.getSalvoes().size() + 1, gamePlayer, salvo.getSalvoLocations()));
        saveScore(getState(gamePlayer), gamePlayer);
        return new ResponseEntity<>(makeMap("gpid", gamePlayer.getId()), HttpStatus.CREATED);

    }

    public void saveScore(String state, GamePlayer gamePlayer) {
        // if state es WON TIE o LOST
        //if (state=="WON" ||)state=="TIE" || state=="LOST") {
        double score = 0;
        switch (state) {
            case "WON":
                score = 1;
                break;
            case "TIE":
                score = 0.5;
                break;
            case "LOST":
                score = 0;
                break;
            default:
                return;
            //   }
        }
        scoreRepository.save(new Score(gamePlayer.getGame(), gamePlayer.getPlayer(), score));
    }


    @RequestMapping(path = "/players", method = RequestMethod.POST)
    public ResponseEntity<Object> CreatePlayer(@RequestParam String email, @RequestParam String password) {

        if (email.isEmpty() || password.isEmpty()) {
            return new ResponseEntity<>(makeMap("error", "Missing Data"), HttpStatus.FORBIDDEN);
        }

        if (playerRepository.findByUserName(email) != null) {
            return new ResponseEntity<>(makeMap("error", "Name already in use"), HttpStatus.FORBIDDEN);
        }

        playerRepository.save(new Player(email, passwordEncoder.encode(password)));
        return new ResponseEntity<>(makeMap("OK", "signup successful"), HttpStatus.CREATED);
    }


    private boolean isGuest(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken;
    }


    @RequestMapping("/game_view/{gamePlayerId}")
    public ResponseEntity<Map<String, Object>> getAllGameViews(Authentication authentication, @PathVariable Long gamePlayerId) {

        GamePlayer gamePlayer = gamePlayerRepository.findById(gamePlayerId).get();

        if (authentication.getName() == gamePlayer.getPlayer().getUserName()) {
            return ResponseEntity.ok(makeGamePlayerDTO2(gamePlayerRepository.findById(gamePlayerId)
                    .get()));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        //return makeGamePlayerDTO2();

    }


    private Map<String, Object> makeGamePlayerDTO2(GamePlayer gamePlayer) {
        Map<String, Object> dto2 = new LinkedHashMap<String, Object>();
        dto2.put("id", gamePlayer.getGame().getId());
        dto2.put("created", gamePlayer.getGame().getCreationDate());
        dto2.put("gameState", getState(gamePlayer));
        dto2.put("gamePlayers", gamePlayer.getGame().getGamePlayers()
                .stream()
                .map(gamePlayer1 -> gamePlayer1.makeGamePlayerDTO())
                .collect(Collectors.toList()));
        dto2.put("ships", gamePlayer.getShips()
                .stream()
                .map(ship -> ship.makeShipDTO())
                .collect(Collectors.toList()));
        dto2.put("salvoes", gamePlayer.getGame().getGamePlayers()
                .stream()
                .flatMap(gamePlayer1 -> gamePlayer1.getSalvoes()
                        .stream()
                        .map(salvo -> salvo.makeSalvoDTO()))
                .collect(Collectors.toList()));
        dto2.put("hits", this.makeHitsDto(gamePlayer));

        return dto2;

    }


    public String getState(GamePlayer gamePlayer) { // estado consulta del juego
        String state = ""; // estadoDelJugadorDelGamePlayerPorReferencia
        if (gamePlayer.getShips().isEmpty()) { // true si no tiene barcos
            state = "PLACESHIPS";
            return state;
        }
        if (!gamePlayer.getOponent().isPresent() || // true: No existe oponente
                gamePlayer.getOponent().isPresent() &&  // true: si existe YY no tiene ships
                        gamePlayer.getOponent().get().getShips().isEmpty()) { // false : si hay oponente y tiene barcos
            state = "WAITINGFOROPP";
            return state;
        }

        if ((gamePlayer.getSalvoes().size()
                == (gamePlayer.getOponent().get().getSalvoes().size()))
                && (!getSunkOpponent(gamePlayer) && (!getSunkOpponent(gamePlayer.getOponent().get())))
        ) {
            // true si no estan en el mismo turno o ninguno destruyo todos los barcos del otro
            state = "PLAY"; // Enter salvos

        } else if ((gamePlayer.getSalvoes().size()
                != (gamePlayer.getOponent().get().getSalvoes().size())))
            state = "WAIT";
        else if (getSunkOpponent(gamePlayer) && (getSunkOpponent(gamePlayer.getOponent().get()))) {
            // Si es Game Over => state = WON,TIE,LOST ?
            state = "TIE";
        } else if (getSunkOpponent(gamePlayer) && (!getSunkOpponent(gamePlayer.getOponent().get())))
            state = "WON";
        else
            state = "LOST";
        return state;

    }



    private Map<String, Object> makeHitsDto(GamePlayer gamePlayer) {
        Map<String, Object> hitsDto = new LinkedHashMap<String, Object>();

        if (gamePlayer.getOponent().isPresent()) {

            hitsDto.put("self", makeHitsGamePlayerDTO(gamePlayer.getOponent().get()));
            hitsDto.put("opponent", makeHitsGamePlayerDTO(gamePlayer));
        } else {
            if (gamePlayer.getOponent().isPresent()) {
                hitsDto.put("self", makeHitsGamePlayerDTO(gamePlayer.getOponent().get()));
            } else {
                hitsDto.put("self", new ArrayList<>());
            }
            hitsDto.put("opponent", makeHitsGamePlayerDTO(gamePlayer));
        }

        return hitsDto;

    }

    private List<Map<String, Object>> makeHitsGamePlayerDTO(GamePlayer gamePlayer) {
        List<Map<String, Object>> hitsGamePlayerDto = new ArrayList<>();


        int carrier = 0;
        int battleship = 0;
        int submarine = 0;
        int destroyer = 0;
        int patrolboat = 0;

        // ---------- variables para las loc de barcos oponentes (Cada turno)
        for (Salvo salvo : orderSalvoes(gamePlayer.getSalvoes())) {

            int carrierHits = 0;
            int battleshipHits = 0;
            int submarineHits = 0;
            int destroyerHits = 0;
            int patrolboatHits = 0;

            // ---------- comparacion salvo con cada barco
            for (Ship ship : gamePlayer.getOponent().get().getShips()) {
                List<String> salvoLocations = new ArrayList<>(salvo.getSalvoLocations());
                salvoLocations.retainAll(ship.getLocations());

                int hitSize = salvoLocations.size();
                if (hitSize != 0) {
                    switch (ship.getType()) {
                        case "carrier":
                            carrier = carrier + hitSize;
                            carrierHits = carrierHits + hitSize;
                            break;
                        case "battleship":
                            battleship = battleship + hitSize;
                            battleshipHits = battleshipHits + hitSize;
                            break;
                        case "submarine":
                            submarine = submarine + hitSize;
                            submarineHits = submarineHits + hitSize;
                            break;
                        case "destroyer":
                            destroyer = destroyer + hitSize;
                            destroyerHits = destroyerHits + hitSize;
                            break;
                        case "patrolboat":
                            patrolboat = patrolboat + hitSize;
                            patrolboatHits = patrolboatHits + hitSize;
                            break;

                    }

                }

            }
            Map<String, Object> damagesDto = new LinkedHashMap<String, Object>();
            Map<String, Object> dtoGPHistas = new LinkedHashMap<>();


            dtoGPHistas.put("turn", salvo.getTurn());
            dtoGPHistas.put("hitLocations", allHitsTurn(salvo));
            dtoGPHistas.put("damages", damagesDto);
            damagesDto.put("carrierHits", carrierHits);
            damagesDto.put("battleshipHits", battleshipHits);
            damagesDto.put("submarineHits", submarineHits);
            damagesDto.put("destroyerHits", destroyerHits);
            damagesDto.put("patrolboatHits", patrolboatHits);
            damagesDto.put("carrier", carrier);
            damagesDto.put("battleship", battleship);
            damagesDto.put("submarine", submarine);
            damagesDto.put("destroyer", destroyer);
            damagesDto.put("patrolboat", patrolboat);

            dtoGPHistas.put("missed", 5 - allHitsTurn(salvo).size());

            hitsGamePlayerDto.add(dtoGPHistas);
        }
        return hitsGamePlayerDto;
    }


    private boolean getSunkOpponent(GamePlayer gamePlayer) {


        int carrier = 0;
        int battleship = 0;
        int submarine = 0;
        int destroyer = 0;
        int patrolboat = 0;


        // ---------- variables para las loc de barcos oponentes (Cada turno)
        for (Salvo salvo : gamePlayer.getSalvoes()) {


            // ---------- comparacion salvo con cada barco
            for (Ship ship : gamePlayer.getOponent().get().getShips()) {
                List<String> salvoLocations = new ArrayList<>(salvo.getSalvoLocations());
                salvoLocations.retainAll(ship.getLocations());

                int hitSize = salvoLocations.size();
                if (hitSize != 0) {
                    switch (ship.getType()) {
                        case "carrier":
                            carrier = carrier + hitSize;
                            break;
                        case "battleship":
                            battleship = battleship + hitSize;
                            break;
                        case "submarine":
                            submarine = submarine + hitSize;
                            break;
                        case "destroyer":
                            destroyer = destroyer + hitSize;
                            break;
                        case "patrolboat":
                            patrolboat = patrolboat + hitSize;
                            break;

                    }

                }

            }


        }

        return (carrier == 5 && battleship == 4 && submarine == 3 && destroyer == 3 && patrolboat == 2);

    }

    private List<String> allHitsTurn(Salvo salvo) {
        if (salvo.getGamePlayer().getOponent().isPresent()) {
            List<String> list = salvo.getGamePlayer().getOponent().get().getShips()
                    .stream().flatMap(ship -> ship.getLocations().stream()).collect(Collectors.toList());
            list.retainAll(salvo.getSalvoLocations());
            return list;
        } else {
            List<String> list = new ArrayList<>();
            return list;
        }
    }


    private Map<String, Object> makeMap(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private List<Salvo> orderSalvoes(Set<Salvo> salvoes) {
        return salvoes
                .stream()
                .sorted(Comparator.comparing(Salvo::getTurn))
                .collect(Collectors.toList());
    }
}


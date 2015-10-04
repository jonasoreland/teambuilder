package org.oreland.db;

import org.oreland.entity.Game;
import org.oreland.entity.Player;
import org.oreland.entity.Training;

import java.util.HashMap;

/**
 * Created by jonas on 10/4/15.
 */
public class Repository {
    HashMap<String, Game> games = new HashMap<>();
    HashMap<String, Training> trainings = new HashMap<>();
    HashMap<String, Player> players = new HashMap<>();

    public Player add(Player p) {
        if (!players.containsKey(p.ssno)) {
            players.put(p.ssno, p);
        }
        return players.get(p.ssno);
    }

    public Game add(Game game) {
        if (!games.containsKey(game.id)) {
            games.put(game.id, game);
        }
        return games.get(game.id);
    }

    public Training add(Training training) {
        if (!trainings.containsKey(training.id)) {
            trainings.put(training.id, training);
        }
        return trainings.get(training.id);
    }
};

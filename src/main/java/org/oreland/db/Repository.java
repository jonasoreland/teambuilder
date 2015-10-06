package org.oreland.db;

import org.oreland.entity.Activity;
import org.oreland.entity.Player;

import java.util.HashMap;

/**
 * Created by jonas on 10/4/15.
 */
public class Repository {
    HashMap<String, Activity> activities = new HashMap<>();
    HashMap<String, Player> players = new HashMap<>();

    public Player add(Player p) {
        if (!players.containsKey(p.ssno)) {
            players.put(p.ssno, p);
        }
        return players.get(p.ssno);
    }

    public Activity add(Activity game) {
        if (!activities.containsKey(game.id)) {
            activities.put(game.id, game);
        }
        return activities.get(game.id);
    }

    public Iterable<Activity> getActivities() {
        return activities.values();
    }
};

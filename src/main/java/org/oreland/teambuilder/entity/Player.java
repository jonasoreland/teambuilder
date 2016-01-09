package org.oreland.teambuilder.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class Player {

    public Player(String first_name, String last_name) {
        this.first_name = first_name.trim();
        this.last_name = last_name.trim();
        this.guest = false;
    }

    public Player copy() {
        Player p = new Player(first_name, last_name);
        p.type = type;
        p.level_history.addAll(level_history);
        p.target_level = target_level;
        p.guest = guest;
        return p;
    }

    public String toString() {
        return "[ " + first_name + " " + last_name + " ]";
    }

    static public class LevelHistoryEntry {
        public Date date;
        public TargetLevel level;
    }

    public enum Type {
        PLAYER,
        LEADER;

        public static Type parse(String s) {
            String tmp = s.toLowerCase();
            if (tmp.matches("ledare"))
                return LEADER;
            if (tmp.matches("deltagare"))
                return PLAYER;
            return null;
        }

        public String toString() {
            switch (this) {
                case PLAYER:
                    return "Deltagare";
                case LEADER:
                    return "Ledare";
            }
            return null;
        }
    }

    public String first_name;
    public String last_name;
    public boolean guest;
    public Type type;
    public TargetLevel target_level;
    public List<LevelHistoryEntry> level_history = new ArrayList<>();

    /**
     * cross referenced
     **/
    public List<Activity> games_played = new ArrayList<>();     // games played
    public List<Activity.Invitation> games_invited = new ArrayList<>();

    public void add(Activity act) {
        for (Activity a : games_played) {
            if (a.id.contentEquals(act.id)) {
                return;
            }
        }
        games_played.add(act);
    }

    public void add(Activity.Invitation invitation) {
        games_invited.add(invitation);
    }

}

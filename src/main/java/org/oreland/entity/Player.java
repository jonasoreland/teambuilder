package org.oreland.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class Player {
    static public class LevelHistoryEntry {
        public Date date;
        public TargetLevel level;
    }

    ;

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

    ;

    public String first_name;
    public String last_name;
    public Type type;
    public TargetLevel target_level;
    public List<LevelHistoryEntry> level_history = new ArrayList<>();

    /**
     * cross referenced
     **/
    public List<Activity> games_played = new ArrayList<>();     // games played
    public List<Activity.Invitation> games_invited = new ArrayList<>();
}

package org.oreland.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class Player {
    public class LevelHistoryEntry {
        public Date date;
        public TargetLevel level;
    };

    public String ssno;
    public String first_name;
    public String last_name;
    public List<LevelHistoryEntry> level_history = new ArrayList<>();

    /** cross referenced **/
    public List<Game> games_played = new ArrayList<>();     // games played
}

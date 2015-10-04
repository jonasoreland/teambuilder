package org.oreland.entity;

import java.util.Date;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class Player {
    public class LevelHistoryEntry {
        Date date;
        TargetLevel level;
    };

    String first_name;
    String last_name;
    List<LevelHistoryEntry> level_history;
}

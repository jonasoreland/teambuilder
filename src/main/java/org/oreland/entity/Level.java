package org.oreland.entity;

import org.oreland.db.Repository;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by jonas on 10/1/15.
 */
public class Level {

    public String name;

    public String toString() {
        return name;
    }

    public static Level parseOrCreate(Repository repo, String level) {
        if (level == null || level.isEmpty())
            return null;

        Level l = parse(repo, level);
        if (l == null) {
            l = new Level();
            l.name = level;
            repo.addLevel(l);
        }
        return l;
    }

    public static Level parse(Repository repo, String level) {
        if (level == null || level.isEmpty())
            return null;

        Set<Level> levels = new HashSet<>();
        for (Level l : repo.getLevels()) {
            if (level.contains(l.name))
                levels.add(l);
        }
        if (levels.size() == 1)
            return levels.iterator().next();
        return null;
    }
}

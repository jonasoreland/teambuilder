package org.oreland.teambuilder.entity;

import org.oreland.teambuilder.db.Repository;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by jonas on 10/1/15.
 */
public class Level {

    public String str;
    public String names[];

    public Level(String str) {
        this.str = str;
        this.names = str.split("/");
    }

    public String toString() {
        return str;
    }

    public static Level parseOrCreate(Repository repo, String str) {
        if (str == null || str.isEmpty())
            return null;

        Level l = parse(repo, str);
        if (l == null) {
            l = new Level(str);
            repo.addLevel(l);
        }
        return l;
    }

    public static Level parse(Repository repo, String str) {
        if (str == null || str.isEmpty())
            return null;

        Set<Level> levels = new HashSet<>();
        for (Level l : repo.getLevels()) {
            if (l.Match(str))
                levels.add(l);
        }
        if (levels.size() == 1)
            return levels.iterator().next();
        return null;
    }

    public int compare(Repository repo, Level level) {
        int a = repo.getLevelIndex(this);
        int b = repo.getLevelIndex(level);
        return a - b;
    }

    public boolean Match(String str) {
        for (String name : this.names) {
            if (str.contains(name))
                return true;
        }
        return false;
    }
}

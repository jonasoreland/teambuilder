package org.oreland.entity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.oreland.db.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class TargetLevel {

    public class Distribution {
        public Level level;
        public int count;
    }

    public List<Distribution> distribution = new ArrayList<Distribution>();

    static public TargetLevel parseJson(Repository repo, String str) {
        if (str == null || str.isEmpty())
            return null;

        try {
            TargetLevel target = new TargetLevel();
            JSONArray arr = new JSONObject(str).getJSONArray("target");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = (String)obj.keys().next();
                int count = obj.getInt(name);
                Level level = Level.parseOrCreate(repo, name);
                Distribution dist = target.getOrCreate(level);
                dist.count += count;
            }
            return target;
        } catch (Exception ex) {
        }
        return null;
    }

    String toJson() {
        return null;
    }

    public boolean equal(TargetLevel other) {
        if (distribution.size() != other.distribution.size())
            return false;
        for (int i = 0; i < distribution.size(); i++) {
            Distribution d = distribution.get(i);
            Distribution d2 = other.get(d.level);
            if (d2 == null || d.count != d2.count)
                return false;
        }
        return true;
    }

    public Distribution get(Level l) {
        for (int i = 0; i < distribution.size(); i++) {
            if (distribution.get(i).level == l)
                return distribution.get(i);
        }
        return null;
    }

    public Distribution getOrCreate(Level l) {
        Distribution d = get(l);
        if (d == null) {
            d = new Distribution();
            d.level = l;
            d.count = 0;
        }
        return d;
    }
}

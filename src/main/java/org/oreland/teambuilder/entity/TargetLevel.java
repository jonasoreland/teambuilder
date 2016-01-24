package org.oreland.teambuilder.entity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.oreland.teambuilder.db.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class TargetLevel {

    public class Distribution {
        public Level level;
        public double count;
    }

    public List<Distribution> distribution = new ArrayList<Distribution>();

    public TargetLevel() {
    }

    public TargetLevel(TargetLevel target_level) {
        for (Distribution d : target_level.distribution) {
            Distribution new_d = getOrCreate(d.level);
            new_d.count = d.count;
        }
    }

    static public TargetLevel parseJson(Repository repo, String str) {
        if (str == null || str.isEmpty())
            return null;

        try {
            TargetLevel target = new TargetLevel();
            JSONArray arr = new JSONObject(str).getJSONArray("target");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = (String) obj.keys().next();
                int count = obj.getInt(name);
                Level level = Level.parseOrCreate(repo, name);
                Distribution dist = target.getOrCreate(level);
                dist.count += count;
                target.distribution.add(dist);
            }
            return target;
        } catch (Exception ex) {
        }
        return null;
    }

    public String toJson() {
        JSONArray arr = new JSONArray();
        for (Distribution d : distribution) {
            JSONObject obj = new JSONObject();
            obj.put(d.level.name, d.count);
            arr.put(obj);
        }
        JSONObject obj2 = new JSONObject();
        obj2.put("target", arr);
        return obj2.toString();
    }

    public boolean equal(TargetLevel other) {
        if (distribution.size() != other.distribution.size())
            return false;
        for (Distribution d : distribution) {
            Distribution d2 = other.get(d.level);
            if (d2 == null || d.count != d2.count)
                return false;
        }
        return true;
    }

    public Distribution get(Level l) {
        for (Distribution d : distribution) {
            if (d.level == l)
                return d;
        }
        return null;
    }

    public Distribution getOrCreate(Level l) {
        Distribution d = get(l);
        if (d == null) {
            d = new Distribution();
            d.level = l;
            d.count = 0;
            distribution.add(d);
        }
        return d;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("[ ");
        for (Distribution d : distribution) {
            str.append(d.level);
            str.append(":");
            str.append(d.count);
            str.append(" ");
        }
        str.append("]");
        return str.toString();
    }

    public void normalize() {
        double sum = 0;
        for (Distribution d : distribution) {
            sum += d.count;
        }
        if (sum == 0)
            return;
        for (Distribution d : distribution) {
            d.count /= sum;
        }
    }
    public Level getBestMatchLevel() {
        double sum = 0;
        for (Distribution d : distribution) {
            sum += d.count;
        }
        sum /= 2;
        for (Distribution d : distribution) {
            if (d.count >= sum) {
                return d.level;
            }
            sum -= d.count;
        }
        return null;
    }

    void add (double count) {
        for (Distribution d : distribution) {
            d.count += count;
        }
    }

    public Level getNextGameLevel(TargetLevel played) {
        TargetLevel tl = new TargetLevel(this);
        for (Distribution d : played.distribution) {
            Distribution d2 = tl.get(d.level);
            while (d.count >= d2.count) {
                tl.add(1);
            }
            d2.count -= d.count;
        }
        Level l = null;
        double max = 0;
        for (Distribution d : tl.distribution) {
            if (d.count > max) {
                l = d.level;
                max = d.count;
            }
        }
        return l;
    }
}

package org.oreland.analysis;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.oreland.db.Repository;
import org.oreland.entity.Activity;
import org.oreland.entity.Player;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jonas on 10/10/15.
 */
public class Analysis {

    Repository repo;

    public static final double LIMIT_VERY = 0.8;
    public static final double LIMIT_LITTLE = 0.3;

    public Analysis(Repository repo) {
        this.repo = repo.clone();
        // merge activities that has been artificially split
        // to increase LOK...(e.g training same date/time)
        mergeSplitActivities();
    }

    private void mergeSplitActivities() {
        List<Activity> new_list = new ArrayList<>();
        {
            Iterator<Activity> a = getCompletedTraining();
            while (a.hasNext()) {
                new_list.add(a.next());
            }
        }
        Collections.sort(new_list, new Comparator<Activity>() {
            @Override
            public int compare(Activity a1, Activity a2) {
                return a1.date.compareTo(a2.date);
            }
        });

        List<Activity> merge = new ArrayList<>();
        List<Activity> remove = new ArrayList<>();
        for (Activity a : new_list) {
            if (merge.isEmpty())
                merge.add(a);
            else if (a.mergeable(merge.iterator().next())) {
                merge.add(a);
            } else {
                merge(merge, remove);
                merge.clear();
                merge.add(a);
            }
        }
        if (!merge.isEmpty()) {
            merge(merge, remove);
        }
        for (Activity a : remove) {
            repo.remove(a);
        }
    }

    private void merge(List<Activity> merge, List<Activity> remove) {
        Iterator<Activity> it = merge.iterator();
        Activity keep = it.next();
        while (it.hasNext()) {
            Activity dup = it.next();
            merge(keep, dup);
            remove.add(dup);
        }
    }

    private void merge(Activity keep, Activity dup) {
        for (Activity.Invitation inv : dup.invitations) {
            inv.player.games_invited.remove(dup);
            repo.addInvitation(keep, inv);
        }
        for (Activity.Participant part : dup.participants) {
            part.player.games_played.remove(dup);
            repo.addParticipant(keep, part.player);
        }
    }

    public void report() {
        System.out.println("Antal träningar: " + count(getCompletedTraining()));
        System.out.println("Barn per träning: " + new Stat<Activity>().toString(getCompletedTraining(), new PerMatch(Player.Type.PLAYER)));
        System.out.println("Träning per barn: " + new Stat<Player>().toString(getPlayers(), new PerPlayer(Activity.Type.TRAINING)));
        System.out.println("Antal matcher: " + count(getCompletedGames()));
        System.out.println("Match per barn: " + new Stat<Player>().toString(getPlayers(), new PerPlayer(Activity.Type.GAME)));
        System.out.println("Cup per barn: " + new Stat<Player>().toString(getPlayers(), new PerPlayer(Activity.Type.CUP)));

        System.out.println("Ledartäthet");
        System.out.println("Barn per ledare match: " + new Stat<Activity>().toString(getCompletedGames(), new BarnPerLedare()));
        System.out.println("Barn per ledare träning: " + new Stat<Activity>().toString(getCompletedTraining(), new BarnPerLedare()));

        System.out.println("Segmentering >=" + (int)(100*LIMIT_VERY) +"% närvaro, <" + (int)(100*LIMIT_LITTLE) + "% närvaro");

        // For Narvaro, if training has 0 invitations then everyone is invited
        {
          Iterator<Activity> it = getCompletedTraining();
          while (it.hasNext()) {
            Activity act = it.next();
            if (countInvitedPlayers(act) == 0) {
              Iterator<Player> it2 = getPlayers();
              while (it2.hasNext()) {
                Player p = it2.next();
                Activity.Invitation inv = new Activity.Invitation();
                inv.player = p;
                repo.addInvitation(act, inv);
              }
            }
          }
        }

        // For Narvaro, if player participated, player was also invited
        {
          Iterator<Player> it2 = getPlayers();
          while (it2.hasNext()) {
            Player p = it2.next();
            for (Activity act : p.games_played) {
              if (!was_invited(p, act)) {
                Activity.Invitation inv = new Activity.Invitation();
                inv.player = p;
                repo.addInvitation(act, inv);
              }
            }
          }
        }

        List<List<Player>> segment = new ArrayList<List<Player>>();
        segment.add(new ArrayList<Player>());
        segment.add(new ArrayList<Player>());
        segment.add(new ArrayList<Player>());
        {
            Iterator<Player> it = getPlayers();
            Measure<Player> narvaro = new Narvaro();
            while (it.hasNext()) {
              Player p = it.next();
              double n = narvaro.getValue(p);
              if (n >= LIMIT_VERY)
                segment.get(0).add(p);
              else if (n < LIMIT_LITTLE)
                segment.get(2).add(p);
              else
                segment.get(1).add(p);
            }
        }

        String names[] = new String[3];
        names[0] = "Mycket intresserade";
        names[1] = "Normal intresserade";
        names[2] = "Lite intresserade";
        for (int i = 0; i < segment.size(); i++) {
          System.out.println(names[i] + ": " + segment.get(i).size() + " " + (int)(100*segment.get(i).size()/count(getPlayers())) +"%");
          if (segment.get(i).size() == 0)
            continue;
          System.out.println("Match per barn: " + new Stat<Player>().toString(segment.get(i).iterator(), new PerPlayer(Activity.Type.GAME)));
          System.out.println("Träning per barn: " + new Stat<Player>().toString(segment.get(i).iterator(), new PerPlayer(Activity.Type.TRAINING)));
          System.out.println("Cup per barn: " + new Stat<Player>().toString(segment.get(i).iterator(), new PerPlayer(Activity.Type.CUP)));
        }
    }

    boolean was_invited(Player p, Activity act) {
      for (Activity.Invitation inv : act.invitations) {
        if (inv.player == p)
          return true;
      }
      return false;
    }

    private abstract class Filter<T> {
        public abstract boolean OK(T t);
    }

    private abstract class Measure<T> {
        public abstract double getValue(T t);
    }

    public class Stat<T> {
        public String toString(Iterator<T> iterator, Measure<T> measure) {
            DescriptiveStatistics stat = new DescriptiveStatistics();
//            SummaryStatistics stat = new SummaryStatistics();
            while (iterator.hasNext()) {
                T t = iterator.next();
                stat.addValue(measure.getValue(t));
            }
            StringBuilder sb = new StringBuilder();
            sb.append("avg: " + String.format("%.2f", stat.getMean()));
            sb.append(" min/max: " + String.format("%.1f/%.1f", stat.getMin(),stat.getMax()));
            sb.append(" stdev: " + String.format("%.2f", stat.getStandardDeviation()));
            return sb.toString();
        }
    };

    private int count(Iterator iterator) {
        int count = 0;
        while (iterator.next() != null)
            count++;
        return count;
    }

    public int countUngradedPlayers() {
        return count(getUngradedPlayers());
    }

    private class BarnPerLedare extends Measure<Activity> {
        @Override
        public double getValue(Activity act) {
            double ledare = 0;
            double barn = 0;
            for (Activity.Participant p : act.participants) {
                if (p.player.type == Player.Type.LEADER)
                    ledare++;
                if (p.player.type == Player.Type.PLAYER)
                    barn++;
            }
            return  barn / ledare;
        }
    };

    private class PerMatch extends Measure<Activity> {
        Player.Type type;
        public PerMatch(Player.Type type) {
          this.type = type;
        }

        @Override
        public double getValue(Activity act) {
            double found = 0;
            for (Activity.Participant p : act.participants) {
                if (p.player.type == type)
                    found++;
            }
            return found;
        }
    };

    private class PerPlayer extends Measure<Player> {
        Activity.Type type;
        public PerPlayer(Activity.Type type) {
          this.type = type;
        }

        @Override
        public double getValue(Player player) {
            double cnt = 0;
            for (Activity act : player.games_played) {
                if (act.type == type)
                    cnt++;
            }
            return cnt;
        }
    };

    private class Narvaro extends Measure<Player> {
        @Override
        public double getValue(Player player) {
          double val = player.games_played.size();
          if (player.games_invited.size() == 0)
            return 0;

          val /= player.games_invited.size();
          return val;
        }
    }

    public Iterator<Activity> getCompletedGames() {
        return new FilteredIterator<>(repo.getActivities().iterator(), new Filter<Activity>() {
            @Override
            public boolean OK(Activity activity) {
                return activity.synced == true && activity.type == Activity.Type.GAME;
            }
        });
    }

    public Iterator<Activity> getCompletedTraining() {
        return new FilteredIterator<>(repo.getActivities().iterator(), new Filter<Activity>() {
            @Override
            public boolean OK(Activity activity) {
                return activity.synced == true && activity.type == Activity.Type.TRAINING;
            }
        });
    }

    public Filter<Player> PlayerFilter = new Filter<Player>() {
        @Override
        public boolean OK(Player player) {
            return player.guest == false && player.type == Player.Type.PLAYER;
        }
    };

    public Iterator<Player> getPlayers() {
        return new FilteredIterator<>(repo.getPlayers(), PlayerFilter);
    }

    public int countInvitedPlayers(Activity act) {
        int count = 0;
        for (Activity.Invitation inv : act.invitations) {
          if (PlayerFilter.OK(inv.player))
            count++;
        }
        return count;
    }

    public Iterator<Player> getUngradedPlayers() {
        return new FilteredIterator<>(repo.getPlayers(), new Filter<Player>() {
            @Override
            public boolean OK(Player player) {
                return player.type == Player.Type.PLAYER && player.level_history.isEmpty();
            }
        });
    }

    private class FilteredIterator<T> implements Iterator<T> {

        private final Iterator<T> all;
        private final Filter<T> filter;
        private T next = null;

        FilteredIterator(Iterator<T> all, Filter<T> filter) {
            this.all = all;
            this.filter = filter;
        }

        @Override
        public boolean hasNext() {
            next = getNext();
            if (next != null)
                return true;
            return false;
        }

        @Override
        public T next() {
            if (next != null) {
                T p = next;
                next = null;
                return p;
            }
            return getNext();
        }

        private T getNext() {
            while (true) {
                if (!all.hasNext())
                    return null;
                T p = all.next();
                if (p == null)
                    return null;
                if (filter.OK(p))
                    return p;
            }
        }

        @Override
        public void remove() {

        }
    }
}

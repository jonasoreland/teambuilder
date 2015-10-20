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

    public Analysis(Repository repo) {
        this.repo = repo.clone();
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
            if (a.mergeable(merge.iterator().next())) {
                merge.add(a);
            } else {
                if (merge.size() > 1) {
                    merge(merge, remove);
                }
                merge.clear();
            }
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
        System.out.println("Merge " + dup + " into " + keep);
        for (Activity.Invitation inv : dup.invitations) {
            inv.player.games_invited.remove(dup);
            repo.addInvitation(keep, inv);
        }
        System.out.println("Merging " + dup + " into " + keep);
        for (Activity.Participant part : dup.participants) {
            part.player.games_played.remove(dup);
            repo.addParticipant(keep, part.player);
        }
        System.out.println("Merged " + dup + " into " + keep);
    }

    public void report() {
        System.out.println("Antal tr?ningar: " + count(getCompletedTraining()));
        System.out.println("Barn per tr?ning: " + new Stat<Activity>().toString(getCompletedTraining(), new BarnPerMatch(), false));
        System.out.println("Barn per ledare tr?ning: " + new Stat<Activity>().toString(getCompletedTraining(), new BarnPerLedare(), false));
        System.out.println("Tr?ning per barn: " + new Stat<Player>().toString(getPlayers(), new TrainingPerPlayer(), true));
        System.out.println("Antal matcher: " + count(getCompletedGames()));
        System.out.println("Barn per ledare match: " + new Stat<Activity>().toString(getCompletedGames(), new BarnPerLedare(), false));
        System.out.println("Match per barn: " + new Stat<Player>().toString(getPlayers(), new MatchPerPlayer(), true));
    }

    private abstract class Filter<T> {
        public abstract boolean OK(T t);
    }

    private abstract class Measure<T> {
        public abstract double getValue(T t);
    }

    public class Stat<T> {
        public String toString(Iterator<T> iterator, Measure<T> measure, boolean print_percent) {
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
            if (print_percent) {
                double percentil[] = new double[3];
                double percent = 100.0 / (percentil.length + 1);
                for (int i = 0; i < percentil.length; i++) {
                    percentil[i] = stat.getPercentile((i + 1) * percent);
                }
                sb.append(" percentil: [ ");
                for (int i = 0; i < percentil.length; i++) {
                    sb.append(String.format(" %.1f", percentil[i]));
                }
                sb.append(" ]");
            }
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

    private class BarnPerMatch extends Measure<Activity> {
        @Override
        public double getValue(Activity act) {
            double barn = 0;
            for (Activity.Participant p : act.participants) {
                if (p.player.type == Player.Type.PLAYER)
                    barn++;
            }
            return  barn;
        }
    };

    private class MatchPerPlayer extends Measure<Player> {
        @Override
        public double getValue(Player player) {
            double cnt = 0;
            for (Activity act : player.games_played) {
                if (act.type == Activity.Type.GAME)
                    cnt++;
            }
            return cnt;
        }
    };

    private class TrainingPerPlayer extends Measure<Player> {
        @Override
        public double getValue(Player player) {
            double cnt = 0;
            for (Activity act : player.games_played) {
                if (act.type == Activity.Type.TRAINING)
                    cnt++;
            }
            return cnt;
        }
    };

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

    public Iterator<Player> getPlayers() {
        return new FilteredIterator<>(repo.getPlayers(), new Filter<Player>() {
            @Override
            public boolean OK(Player player) {
                return player.type == Player.Type.PLAYER;
            }
        });
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

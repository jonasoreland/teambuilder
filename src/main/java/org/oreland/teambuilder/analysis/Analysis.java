package org.oreland.teambuilder.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.oreland.teambuilder.db.Repository;
import org.oreland.teambuilder.entity.Activity;
import org.oreland.teambuilder.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    public static CSVPrinter reportHeader(Appendable out) throws Exception {
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader(
                "lag", "period",
                "#träningar", "barn/träning", "träning/barn", "barn/ledare", "#matcher", "match/barn", "#cup",
                "#mycket intresserade", "träning/barn ", "match/barn ",
                "#resten", "träning/barn  ", "match/barn  ",
                "#lite intresserade", "träning/barn   ", "match/barn   ").print(out);
        return printer;
    }

    public void report(CSVPrinter printer, String team, String period) throws Exception {
        int cnt_training = count(getCompletedTraining());
        Stat<Activity> barn_per_tranining = new Stat<Activity>(getCompletedTraining(), new PerMatch(Player.Type.PLAYER));
        Stat<Player> training_per_barn = new Stat<Player>(getPlayers(), new PerPlayer(Activity.Type.TRAINING));
        int cnt_games = count(getCompletedGames());
        if (cnt_training == 0 && cnt_games == 0)
            return;
        Stat<Player> games_per_barn = new Stat<Player>(getPlayers(), new PerPlayer(Activity.Type.GAME));
        Stat<Player> cups_per_barn = new Stat<Player>(getPlayers(), new PerPlayer(Activity.Type.CUP));
        System.out.println("*** " + team + " - " + period);
        System.out.println("Antal träningar: " + cnt_training);
        System.out.println("Barn per träning: " + barn_per_tranining);
        System.out.println("Träning per barn: " + training_per_barn);
        System.out.println("Antal matcher: " + cnt_games);
        System.out.println("Match per barn: " + games_per_barn);
        System.out.println("Cup per barn: " + cups_per_barn);

        System.out.println("Ledartäthet");
        Stat<Activity> barn_per_ledare_games = new Stat<Activity>(getCompletedGames(), new BarnPerLedare());
        Stat<Activity> barn_per_ledare_training = new Stat<Activity>(getCompletedTraining(), new BarnPerLedare());
        System.out.println("Barn per ledare match: " + barn_per_ledare_games);
        System.out.println("Barn per ledare träning: " + barn_per_ledare_training);

        List<String> rec = new ArrayList<>();
        if (printer != null) {
            rec.add(team);
            rec.add(period);
            rec.add(Integer.toString(cnt_training));
            rec.add(barn_per_tranining.averageToString());
            rec.add(training_per_barn.averageToString());
            rec.add(barn_per_ledare_training.averageToString());
            rec.add(Integer.toString(cnt_games));
            rec.add(games_per_barn.averageToString());
            rec.add(cups_per_barn.averageToString());
        }

        System.out.println("Segmentering >=" + (int) (100 * LIMIT_VERY) + "% närvaro, <" + (int) (100 * LIMIT_LITTLE) + "% närvaro");

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
            System.out.println(names[i] + ": " + segment.get(i).size() + " " + (int) (100 * segment.get(i).size() / count(getPlayers())) + "%");
            if (segment.get(i).size() == 0) {
                if (printer != null) {
                    rec.add(Integer.toString(0));
                    rec.add("");
                    rec.add("");
                }
                continue;
            }
            Stat<Player> s_match_per_barn = new Stat<Player>(segment.get(i).iterator(), new PerPlayer(Activity.Type.GAME));
            Stat<Player> s_training_per_barn = new Stat<Player>(segment.get(i).iterator(), new PerPlayer(Activity.Type.TRAINING));
            System.out.println("Match per barn: " + s_match_per_barn);
            System.out.println("Träning per barn: " + s_training_per_barn);
            System.out.println("Cup per barn: " + new Stat<Player>(segment.get(i).iterator(), new PerPlayer(Activity.Type.CUP)));
            if (printer != null) {
                rec.add(Integer.toString(segment.get(i).size()));
                rec.add(s_training_per_barn.averageToString());
                rec.add(s_match_per_barn.averageToString());
            }
        }
        if (printer != null) {
            printer.printRecord(rec);
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
        DescriptiveStatistics stat = new DescriptiveStatistics();
        //SummaryStatistics stat = new SummaryStatistics();

        public Stat(Iterator<T> iterator, Measure<T> measure) {
            while (iterator.hasNext()) {
                T t = iterator.next();
                stat.addValue(measure.getValue(t));
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("avg: " + String.format("%.2f", stat.getMean()));
            sb.append(" min/max: " + String.format("%.1f/%.1f", stat.getMin(), stat.getMax()));
            sb.append(" stdev: " + String.format("%.2f", stat.getStandardDeviation()));
            return sb.toString();
        }

        public String averageToString() {
            return String.format("%.2f", stat.getMean());
        }
    }

    ;

    private int count(Iterator iterator) {
        int count = 0;
        while (iterator.hasNext() && iterator.next() != null)
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
            return barn / ledare;
        }
    }

    ;

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
    }

    ;

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
    }

    ;

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

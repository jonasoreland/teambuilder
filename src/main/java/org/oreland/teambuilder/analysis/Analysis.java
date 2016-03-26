package org.oreland.teambuilder.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.oreland.teambuilder.db.Filter;
import org.oreland.teambuilder.db.Repository;
import org.oreland.teambuilder.entity.Activity;
import org.oreland.teambuilder.entity.Level;
import org.oreland.teambuilder.entity.Player;
import org.oreland.teambuilder.entity.TargetLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jonas on 10/10/15.
 */
public class Analysis {

    private Repository repo;

    private static final double LIMIT_VERY = 0.8;
    private static final double LIMIT_LITTLE = 0.3;

    private static final double PLAYED_WITH_FEW = 33;
    private static final double PLAYED_WITH_MOST = 66;

    public Analysis(Repository repo) {
        this.repo = repo.clone();

        // remove REQUEST these are not interesting for analysis...
        this.repo.prune(new Filter<Activity>() {
            @Override
            public boolean OK(Activity activity) {
                switch (activity.type) {
                    case GAME:
                    case TRAINING:
                    case CUP:
                        return true;
                    case REQUEST:
                        return false;
                }
                return false;
            }
        });

        // merge activities that has been artificially split
        // to increase LOK...(e.g training same date/time)
        mergeSplitActivities();

        // For Narvaro, if training has 0 invitations then everyone is invited
        {
            for (Activity act : getCompletedTraining()) {
                if (countInvitedPlayers(act) == 0) {
                    for (Player p : getPlayers()) {
                        Activity.Invitation inv = new Activity.Invitation(act);
                        inv.player = p;
                        repo.addInvitation(act, inv);
                    }
                }
            }
        }

        // For Narvaro, if player participated, player was also invited
        {
            for (Player p : getPlayers()) {
                for (Activity act : p.games_played) {
                    if (!was_invited(p, act)) {
                        Activity.Invitation inv = new Activity.Invitation(act);
                        inv.player = p;
                        repo.addInvitation(act, inv);
                    }
                }
            }
        }
    }

    private void mergeSplitActivities() {
        List<Activity> new_list = new ArrayList<>();
        for (Activity a : getCompletedTraining()) {
            new_list.add(a);
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
        repo.xref();
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
            inv.player.games_invited.remove(inv);
            repo.addInvitation(keep, inv);
        }
        for (Activity.Participant part : dup.participants) {
            part.player.games_played.remove(part);
            repo.addParticipant(keep, part.player);
        }
        dup.invitations.clear();
        dup.participants.clear();
    }

    public static CSVPrinter reportHeader(Appendable out) throws Exception {
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader(
                "lag", "period",
                "#träningar", "barn/träning", "träning/barn", "barn/ledare", "#matcher", "match/barn", "#cup/barn", "%NEJ", "%Spelat med",
                "#mycket intresserade", "träning/barn " , "match/barn ", "%NEJ ", "%Spelat med ",
                "#resten", "träning/barn  ", "match/barn  ", "%NEJ  ", "%Spelat med  ",
                "#lite intresserade", "träning/barn   ", "match/barn   ", "%NEJ   ", "%Spelat med   ").print(out);
        return printer;
    }

    public static CSVPrinter playerHeader(Appendable out) throws Exception {
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader(
            "lag", "period", "player",
            "#träningar", "#match", "#cup",
            "%NEJ",
            "%närvaro", "spelat med %", "#nivåer",
            "level 0", "level 1", "level 2").print(out);
        return printer;
    }

    public void report(CSVPrinter printer, CSVPrinter playerPrinter, String team, String period) throws Exception {
        int cnt_training = count(getCompletedTraining().iterator());
        Stat<Activity> barn_per_tranining = new Stat<Activity>(getCompletedTraining(), new PerMatch(Player.Type.PLAYER));
        Stat<Player> training_per_barn = new Stat<Player>(getPlayers(), new PerPlayer(Activity.Type.TRAINING));
        int cnt_games = count(getCompletedGames().iterator());
        if (cnt_training == 0 && cnt_games == 0)
            return;
        Stat<Player> games_per_barn = new Stat<Player>(getPlayers(), new PerPlayer(Activity.Type.GAME));
        Stat<Player> cups_per_barn = new Stat<Player>(getPlayers(), new PerPlayer(Activity.Type.CUP));
        Stat<Player> pct_decline = new Stat<Player>(getPlayers(), new Decline(new Filter<Activity>() {
            @Override
            public boolean OK(Activity activity) {
                return activity.synced && (activity.type == Activity.Type.GAME || activity.type == Activity.Type.CUP);
            }
        }));
        PlayedWithOthers playedWithOthers = new PlayedWithOthers(getPlayers(), getPlayers(), true);
        Stat<Player> playedWithOthersStat = new Stat<Player>(getPlayers(), playedWithOthers);

        System.out.println("*** " + team + " - " + period);
        System.out.println("Antal träningar: " + cnt_training);
        System.out.println("Barn per träning: " + barn_per_tranining);
        System.out.println("Träning per barn: " + training_per_barn);
        System.out.println("Antal matcher: " + cnt_games);
        System.out.println("Match per barn: " + games_per_barn);
        System.out.println("Cup per barn: " + cups_per_barn);
        System.out.println("%NEJ: " + pct_decline);
        System.out.println("Player with others: " + playedWithOthersStat);

        System.out.println("Ledartäthet");
        Stat<Activity> barn_per_ledare_games = new Stat<Activity>(getCompletedGames(), new BarnPerLedare());
        Stat<Activity> barn_per_ledare_training = new Stat<Activity>(getCompletedTraining(), new BarnPerLedare());
        System.out.println("Barn per ledare match: " + barn_per_ledare_games);
        System.out.println("Barn per ledare träning: " + barn_per_ledare_training);

        // Compute player2player matrix
        double[] buckets = new double[]{ PLAYED_WITH_FEW, PLAYED_WITH_MOST};
        Hist<Player> played_with_others = new Hist<>(buckets, getPlayers(), new PlayedWithOthers(getPlayers(), getPlayers(), true));
        System.out.println("Played with others: " + played_with_others.toString());
        {
            System.out.println("Played level: " + new FullHist<>(getPlayers(), new LevelsPerPlayer()).toString("%d barn spelade %d nivåer"));
        }
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
            rec.add(pct_decline.averageToString());
            rec.add(playedWithOthersStat.averageToString());
        }

        System.out.println("Segmentering >=" + (int) (100 * LIMIT_VERY) + "% närvaro, <" + (int) (100 * LIMIT_LITTLE) + "% närvaro");

        List<List<Player>> segment = new ArrayList<List<Player>>();
        segment.add(new ArrayList<Player>());
        segment.add(new ArrayList<Player>());
        segment.add(new ArrayList<Player>());
        for (Player p : getPlayers()) {
            Measure<Player> narvaro = new Narvaro();
            double n = narvaro.getValue(p);
            if (n >= LIMIT_VERY)
                segment.get(0).add(p);
            else if (n < LIMIT_LITTLE)
                segment.get(2).add(p);
            else
                segment.get(1).add(p);
        }

        String names[] = new String[3];
        names[0] = "Mycket intresserade";
        names[1] = "Normal intresserade";
        names[2] = "Lite intresserade";
        for (int i = 0; i < segment.size(); i++) {
            System.out.println(names[i] + ": " + segment.get(i).size() + " " + (int) (100 * segment.get(i).size() / count(getPlayers().iterator())) + "%");
            if (segment.get(i).size() == 0) {
                if (printer != null) {
                    rec.add(Integer.toString(0));
                    rec.add("");
                    rec.add("");
                    rec.add("");
                    rec.add("");
                }
                continue;
            }
            Stat<Player> s_match_per_barn = new Stat<Player>(segment.get(i), new PerPlayer(Activity.Type.GAME));
            Stat<Player> s_training_per_barn = new Stat<Player>(segment.get(i), new PerPlayer(Activity.Type.TRAINING));
            Stat<Player> s_decline_per_barn = new Stat<Player>(segment.get(i), new Decline(new Filter<Activity>() {
                @Override
                public boolean OK(Activity activity) {
                    return activity.synced && (activity.type == Activity.Type.GAME || activity.type == Activity.Type.CUP);
                }
            }));
            Stat<Player> s_played_with_others = new Stat<Player>(segment.get(i), playedWithOthers);
            System.out.println("Match per barn: " + s_match_per_barn);
            System.out.println("Träning per barn: " + s_training_per_barn);
            System.out.println("Cup per barn: " + new Stat<Player>(segment.get(i), new PerPlayer(Activity.Type.CUP)));
            System.out.println("%NEJ: " + s_decline_per_barn);
            System.out.println("%Spelat med: " + s_played_with_others);
            if (printer != null) {
                rec.add(Integer.toString(segment.get(i).size()));
                rec.add(s_training_per_barn.averageToString());
                rec.add(s_match_per_barn.averageToString());
                rec.add(s_decline_per_barn.averageToString());
                rec.add(s_played_with_others.averageToString());
            }
        }

        if (printer != null) {
            printer.printRecord(rec);
        }

        if (playerPrinter != null) {
            for (Player p : getPlayers()) {
                rec = new ArrayList<>();
                rec.add(team);
                rec.add(period);
                rec.add(p.toString());
                rec.add(Integer.toString(count(new FilteredIterable<>(p.games_played, new Filter<Activity>() {
                    @Override
                    public boolean OK(Activity activity) {
                        return activity.synced == true && activity.type == Activity.Type.TRAINING;
                    }
                }).iterator())));
                rec.add(Integer.toString(count(new FilteredIterable<>(p.games_played, new Filter<Activity>() {
                    @Override
                    public boolean OK(Activity activity) {
                        return activity.synced == true && activity.type == Activity.Type.GAME;
                    }
                }).iterator())));
                rec.add(Integer.toString(count(new FilteredIterable<>(p.games_played, new Filter<Activity>() {
                    @Override
                    public boolean OK(Activity activity) {
                        return activity.synced == true && activity.type == Activity.Type.CUP;
                    }
                }).iterator())));
                rec.add(Integer.toString((int) (new Decline(new Filter<Activity>() {
                    @Override
                    public boolean OK(Activity activity) {
                        return activity.synced == true &&
                                (activity.type == Activity.Type.CUP ||
                                        activity.type == Activity.Type.GAME);
                    }
                }).getValue(p))));
                rec.add(Integer.toString((int) (100 * new Narvaro().getValue(p))));
                rec.add(Integer.toString((int) playedWithOthers.getValue(p)));
                TargetLevel tl = new TargetLevel();
                for (Activity act : p.games_played) {
                    if (act.level != null)
                        tl.getOrCreate(act.level).count++;
                }
                rec.add(Integer.toString(tl.distribution.size()));
                for (Level l : repo.getLevels()) {
                    TargetLevel.Distribution d = tl.get(l);
                    if (d != null) {
                        rec.add(d.level.toString() + ":" + Integer.toString((int) d.count));
                    }
                }
                playerPrinter.printRecord(rec);
            }
            playerPrinter.flush();
        }
    }

    private boolean was_invited(Player p, Activity act) {
        for (Activity.Invitation inv : act.invitations) {
            if (inv.player == p)
                return true;
        }
        return false;
    }

    private abstract static class Measure<T> {
        public abstract double getValue(T t);
    }

    public class Stat<T> {
        DescriptiveStatistics stat = new DescriptiveStatistics();
        //SummaryStatistics stat = new SummaryStatistics();

        public Stat(Iterable<T> iterator, Measure<T> measure) {
            for(T t : iterator) {
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

    public class Hist<T> {

        double sum = 0;
        double count[];
        double buckets[];
        double total = 0;

        public Hist(double buckets[], Iterable<T> iterator, Measure<T> measure) {
            this.buckets = buckets;
            int max = buckets.length - 1;
            count = new double[buckets.length];
            for(T t : iterator) {
                total++;
                double val = measure.getValue(t);
                boolean found = false;
                for (int i = 0; i < buckets.length - 1; i++) {
                    if (val < buckets[i]) {
                        found = true;
                        count[i]++;
                        break;
                    }
                }
                if (!found)
                    count[max]++;
                sum += val;
            }
        }

        public String toString() {
            int max = buckets.length - 1;
            StringBuilder sb = new StringBuilder();
            sb.append(Integer.toString((int) (100 * count[0] / total)) + String.format("%% <%.2f ", buckets[0]));
            sb.append(String.format("avg: %.2f ", sum / total));
            sb.append(Integer.toString((int) (100 * count[max] / total)) + String.format("%% >%.2f", buckets[max]));
            return sb.toString();
        }
    }

    public class FullHist<T> {

        double sum = 0;
        double count = 0;
        HashMap<Double, int[]> set = new HashMap<>();

        public FullHist(Iterable<T> iterator, Measure<T> measure) {
            for(T t : iterator) {
                count++;
                double val = measure.getValue(t);
                sum += val;
                if (!set.containsKey(val))
                    set.put(val, new int[1]);
                set.get(val)[0]++;
            }
        }

        public String toString(String pattern) {
            StringBuilder sb = new StringBuilder();
            for (double d : set.keySet()) {
                sb.append(String.format(pattern, set.get(d)[0], (int)d));
                sb.append(" ");
            }
            return sb.toString();
        }
    }

    private static int count(Iterator iterator) {
        int count = 0;
        while (iterator.hasNext() && iterator.next() != null)
            count++;
        return count;
    }

    public int countUngradedPlayers() {
        return count(getUngradedPlayers().iterator());
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

    private Iterable<Activity> getCompletedGames() {
        return new FilteredIterable<>(repo.getActivities(), new Filter<Activity>() {
            @Override
            public boolean OK(Activity activity) {
                return activity.synced == true && activity.type == Activity.Type.GAME;
            }
        });
    }

    private Iterable<Activity> getCompletedTraining() {
        return new FilteredIterable<>(repo.getActivities(), new Filter<Activity>() {
            @Override
            public boolean OK(Activity activity) {
                return activity.synced == true && activity.type == Activity.Type.TRAINING;
            }
        });
    }

    private Filter<Player> PlayerFilter = new Filter<Player>() {
        @Override
        public boolean OK(Player player) {
            return player.guest == false && player.type == Player.Type.PLAYER;
        }
    };

    private Iterable<Player> getPlayers() {
        return new FilteredIterable<>(repo.getPlayers(), PlayerFilter);
    }

    private int countInvitedPlayers(Activity act) {
        int count = 0;
        for (Activity.Invitation inv : act.invitations) {
            if (PlayerFilter.OK(inv.player))
                count++;
        }
        return count;
    }

    private Iterable<Player> getUngradedPlayers() {
        return new FilteredIterable<>(repo.getPlayers(), new Filter<Player>() {
            @Override
            public boolean OK(Player player) {
                return player.type == Player.Type.PLAYER && player.level_history.isEmpty();
            }
        });
    }

    private class FilteredIterable<T> implements Iterable<T> {

        private final Iterator<T> all;
        private final Filter<T> filter;
        private T next = null;

        FilteredIterable(Iterable<T> all, Filter<T> filter) {
            this.all = all.iterator();
            this.filter = filter;
        }

        class MyIterator implements java.util.Iterator<T> {
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

        @Override
        public Iterator<T> iterator() {
            return new MyIterator();
        }
    }

    public static class PlayedWithOthers extends Measure<Player> {
        HashMap<Player, HashSet<Player>> matrix = new HashMap<>();

        public PlayedWithOthers(Iterable<Player> players, Iterable<Player> playedWith,
                                boolean participated) {
            HashSet<Player> playedWithHash = new HashSet<>();
            for (Player p : playedWith) {
                playedWithHash.add(p);
            }
            for (Player p : players) {
                HashSet<Player> set = new HashSet<>();
                if (participated) {
                    for (Activity act : p.games_played) {
                        if (act.type != Activity.Type.GAME)
                            continue;
                        for (Activity.Participant part : act.participants) {
                            if (!playedWithHash.contains(part.player))
                                continue;
                            set.add(part.player);
                        }
                    }
                } else {
                    for (Activity.Invitation inv : p.games_invited) {
                        if (inv.activity.type != Activity.Type.GAME)
                            continue;
                        for (Activity.Invitation part : inv.activity.invitations) {
                            if (!playedWithHash.contains(part.player))
                                continue;
                            set.add(part.player);
                        }
                    }
                }
                matrix.put(p, set);
            }
        }

        @Override
        public double getValue(Player player) {
            HashSet<Player> set = matrix.get(player);
            if (set == null) {
                return 0;
            }
            double cnt = set.size();
            return 100 * (cnt / matrix.size());
        }
    }

    private class LevelsPerPlayer extends Measure<Player> {
        Activity.Type type;

        public LevelsPerPlayer() {
        }

        @Override
        public double getValue(Player player) {
            HashSet<Level> set = new HashSet<>();
            for (Activity act : player.games_played) {
                if (act.type == Activity.Type.GAME && act.level != null)
                    set.add(act.level);
            }
            return set.size();
        }
    }

    private class Decline extends Measure<Player> {
        Filter<Activity> filter;

        public Decline(Filter<Activity> filter) {
            this.filter = filter;
        }

        @Override
        public double getValue(Player player) {
            double invites = 0;
            double played = 0;
            for (Activity a : player.games_played) {
                if (filter.OK(a))
                    played++;
            }
            for (Activity.Invitation a : player.games_invited) {
                if (filter.OK(a.activity))
                    invites++;
            }
            if (invites == 0)
                return 0;
            if (played > invites) {
                System.out.println(player + " played: " + played + ", invites: " + invites);
            }
            return 100 * (1 - (played / invites));
        }
    }
}

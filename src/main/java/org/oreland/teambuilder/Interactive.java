package org.oreland.teambuilder;

import org.apache.commons.csv.CSVPrinter;
import org.oreland.teambuilder.analysis.Analysis;
import org.oreland.teambuilder.sync.Synchronizer;
import org.oreland.teambuilder.sync.Synchronizer.Specifier;
import org.oreland.teambuilder.ui.Dialog;
import org.oreland.teambuilder.ui.DialogBuilder;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.channels.CancelledKeyException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

/**
 * Created by jonas on 12/6/15.
 */
public class Interactive {
    Context ctx;

    public Interactive(Context ctx) {
        this.ctx = ctx;
    }

    public void run() throws Exception {
        while (true) {
            DialogBuilder builder = ctx.builder;
            builder.setQuestion("Choose function");
            List<String> choices = new ArrayList<>();
            choices.add("Quit");
            choices.add("Synchronize (download from MyClub)");
            choices.add("Run statistics and generate report (on downloaded data)");
            builder.setChoices(choices);
            Dialog.Result result = builder.build().show();
            switch (result.intResult) {
                case 1:
                    return;
                case 2:
                    synchronize();
                    break;
                case 3:
                    statistics();
                    break;
            }
        }
    }

    private void synchronize() throws Exception {
        ctx.myclub.init(ctx.prop);
        ctx.myclub.login(ctx.prop, ctx.builder);
        ctx.myclub.setupClub(ctx, ctx.prop, ctx.builder);
        ctx.prop.store(new FileOutputStream("config.properties"), null);
        Selection selection = makeSelection(ctx, ctx.myclub);
        for (Pair<Specifier, List<Specifier>> team : selection.periods) {
            ctx.myclub.setTeam(ctx, team.first);
            for (Specifier period : team.second) {
                ctx.myclub.setPeriod(ctx, period);
                ctx.repo.reset();
                ctx.csv.set(ctx, ctx.myclub);
                ctx.csv.load(ctx);
                ctx.myclub.load(ctx);
                ctx.csv.save(ctx);
            }
        }
    }

    private void statistics() throws Exception {
        Selection selection = makeSelection(ctx, ctx.csv);

        final Appendable out = new FileWriter(ctx.wd + "/report.csv");
        final CSVPrinter printer = Analysis.reportHeader(out);

        List<Pair<Date,Date>> periods = choosePeriods(ctx, selection.periods);

        if (periods == null) {
            // use myclub periods
            for (Pair<Specifier, List<Specifier>> team : selection.periods) {
                ctx.csv.setTeam(ctx, team.first);
                for (Specifier period : team.second) {
                    ctx.repo.reset();
                    ctx.csv.setPeriod(ctx, period);
                    ctx.csv.load(ctx);
                    new Analysis(ctx.repo).report(printer, team.first.name, toString(toUserPeriod(period.name)));
                }
            }
        } else {
            for (Pair<Specifier, List<Specifier>> team : selection.periods) {
                ctx.csv.setTeam(ctx, team.first);
                for (Pair<Date, Date> user_period : periods) {
                    ctx.repo.reset();
                    for (Specifier period : team.second) {
                        // Load all periods contained in user-period
                        if (Contains(toUserPeriod(period.name), user_period)) {
                            ctx.csv.setPeriod(ctx, period);
                            ctx.csv.load(ctx);
                        }
                    }
                    // Prune repo
                    ctx.repo.prune(user_period.first, user_period.second);
                    new Analysis(ctx.repo).report(printer, team.first.name, toString(user_period));
                }
            }

        }
        printer.close();
    }

    private boolean Contains(Pair<Date, Date> period1, Pair<Date, Date> user_period) {
        if (period1.first.after(user_period.first) && period1.first.before(user_period.second))
            return true;
        if (period1.second.after(user_period.first) && period1.second.before(user_period.second))
            return true;
        if (period1.first.before(user_period.first) && period1.second.after(user_period.second))
            return true;
        return false;
    }

    private String toString(Pair<Date, Date> period) {
        return (new SimpleDateFormat("yyyy-MM", Locale.US)).format(period.first);
    }

    // Convert MyClub period name to start/end date
    private Pair<Date, Date> toUserPeriod(String myclub_name) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, Integer.parseInt(myclub_name.substring(3)));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.clear(Calendar.MINUTE);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        if (myclub_name.startsWith("HT")) {
            cal.set(Calendar.MONTH, 7);
            Date startDate = cal.getTime();
            cal.set(Calendar.YEAR, cal.get(Calendar.YEAR) + 1);
            cal.set(Calendar.MONTH, 0);
            Date endDate = cal.getTime();
            System.out.println("period: " + myclub_name + " => " + startDate + ", " + endDate);
            return new Pair<>(startDate, endDate);
        } else {
            cal.set(Calendar.MONTH, 0);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            Date startDate = cal.getTime();
            cal.set(Calendar.MONTH, 7);
            Date endDate = cal.getTime();
            return new Pair<>(startDate, endDate);
        }
    }

    private List<Pair<Date, Date>> choosePeriods(Context ctx,
                                                 List<Pair<Specifier, List<Specifier>>> periods) {
        if (countPeriods(periods) == 1)
            return null;

        List<Specifier> types = new ArrayList<>();
        types.add(new Specifier("MyClub periods - VT (Januari-Juli) HT (Augusti-December)"));
        types.add(new Specifier("Fotboll - VT(April-Juli) HT (Augusti-Oktober) VI(November-Mars)"));
        Specifier choice = DialogBuilder.selectOne(ctx.builder,
                "Select periodization", types);
        if (choice == types.get(0))
            return null; // MyClub periods
        if (choice == types.get(1))
            return soccerPeriods(periods);
        return null;
    }

    private List<Pair<Date, Date>> soccerPeriods(List<Pair<Specifier, List<Specifier>>> periods) {
        // create list of start/end dates according to
        // VT(April-Juli) HT (Augusti-Oktober) VI(November-Mars)
        // for the periods found in periods

        // Find min/max-date
        Date min_date = null;
        Date max_date = null;
        for (Pair<Specifier, List<Specifier>> team : periods) {
            for (Specifier period : team.second) {
                Pair<Date, Date> a = toUserPeriod(period.name);
                if (min_date == null) {
                    min_date = a.first;
                    max_date = a.second;
                } else {
                    if (a.first.before(min_date))
                        min_date = a.first;
                    if (a.second.after(max_date))
                        max_date = a.second;
                }
            }
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(min_date);
        if (cal.get(Calendar.MONTH) == Calendar.JANUARY) {
            cal.add(Calendar.MONTH, -2); // move to November
            min_date = cal.getTime();
        }
        List<Pair<Date,Date>> list = new ArrayList<>();
        while (min_date.before(max_date)) {
            cal.setTime(min_date);
            if (cal.get(Calendar.MONTH) == Calendar.NOVEMBER) {
                cal.add(Calendar.MONTH, 5); // 5 month of winter :(
            } else if (cal.get(Calendar.MONTH) == Calendar.APRIL) {
                cal.add(Calendar.MONTH, 4); // 4 month of spring
            } else if (cal.get(Calendar.MONTH) == Calendar.AUGUST) {
                cal.add(Calendar.MONTH, 3); // 3 month of autumn
            } else {
                StringBuffer s = null;
                s.append("kalle");
            }
            Date end = cal.getTime();
            list.add(new Pair<Date, Date>((Date)min_date.clone(), (Date)end.clone()));
            min_date = end;
        }
        return list;
    }

    private int countPeriods(List<Pair<Specifier, List<Specifier>>> periods) {
        HashSet<String> hash = new HashSet<>();
        for (Pair<Specifier, List<Specifier>> team : periods) {
            for (Specifier period : team.second) {
                hash.add(period.name);
            }
        }
        return hash.size();
    }

    class Selection {
        Selection() {}

        Specifier section;
        List<Specifier> teams;
        List<Pair<Specifier, List<Specifier>>> periods; // Pair<Team, List<Period>>
    }

    private Selection makeSelection(Context ctx, Synchronizer sync) throws Exception {
        Selection selection = new Selection();
        selection.section = DialogBuilder.selectOne(ctx.builder,
                "Select section", sync.listSections(ctx));
        sync.setSection(ctx, selection.section);
        selection.teams = DialogBuilder.selectMulti(ctx.builder,
                "Select teams", sync.listTeams(ctx), true);
        selection.periods = new ArrayList<>();
        List<Specifier> allperiods = new ArrayList<>();
        for (Specifier team : selection.teams) {
            sync.setTeam(ctx, team);
            Pair<Specifier,List<Specifier>> p = new Pair<>(team,
                    sync.listPeriods(ctx));
            selection.periods.add(p);
            allperiods.addAll(p.second);
        }
        HashSet<Specifier> selected_periods = new HashSet<>();
        selected_periods.addAll(DialogBuilder.selectMulti(ctx.builder,
                "Select periods", allperiods, true));

        // remove those periods that were not selected
        for (Pair<Specifier, List<Specifier>> period : selection.periods) {
            // make copy of list, to avoid iterate/modify problem
            List<Specifier> copy = new ArrayList<>();
            copy.addAll(period.second);
            period.second.clear();

            for (Specifier p : copy) {
                if (selected_periods.contains(p)) {
                    period.second.add(p);
                }
            }
        }

        return selection;
    }
}

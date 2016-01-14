package org.oreland.teambuilder;

import org.apache.commons.csv.CSVPrinter;
import org.oreland.teambuilder.analysis.Analysis;
import org.oreland.teambuilder.db.Filter;
import org.oreland.teambuilder.entity.Activity;
import org.oreland.teambuilder.sync.MyClub;
import org.oreland.teambuilder.sync.Synchronizer;
import org.oreland.teambuilder.sync.Synchronizer.Specifier;
import org.oreland.teambuilder.ui.Dialog;
import org.oreland.teambuilder.ui.DialogBuilder;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Created by jonas on 12/6/15.
 */
class Interactive {
    private Context ctx;

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
            choices.add("Construct teams for upcoming games");
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
                case 4:
                    plan();
                    break;
            }
        }
    }

    private void synchronize() throws Exception {
        ctx.myclub.init(ctx.prop);
        ctx.myclub.login(ctx.prop, ctx.builder);
        ctx.myclub.setupClub(ctx, ctx.prop, ctx.builder);
        ctx.prop.store(new FileOutputStream("config.properties"), null);
        Selection selection = makeSelection(ctx, ctx.myclub, true);
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
        Selection selection = makeSelection(ctx, ctx.csv, true);

        final Appendable out = new FileWriter(ctx.wd + "/report.csv");
        final CSVPrinter printer = Analysis.reportHeader(out);

        List<Pair<Date, Date>> periods = choosePeriods(ctx, selection.periods);

        if (periods == null) {
            // use myclub periods
            for (Pair<Specifier, List<Specifier>> team : selection.periods) {
                ctx.csv.setTeam(ctx, team.first);
                for (Specifier period : team.second) {
                    ctx.repo.reset();
                    ctx.csv.setPeriod(ctx, period);
                    ctx.csv.load(ctx);
                    new Analysis(ctx.repo).report(printer, team.first.name, toString(MyClub.periodName2Dates(period.name)));
                }
            }
        } else {
            for (Pair<Specifier, List<Specifier>> team : selection.periods) {
                ctx.csv.setTeam(ctx, team.first);
                for (final Pair<Date, Date> user_period : periods) {
                    ctx.repo.reset();
                    for (Specifier period : team.second) {
                        // Load all periods contained in user-period
                        if (Contains(user_period,
                                MyClub.periodName2Dates(period.name))) {
                            ctx.csv.setPeriod(ctx, period);
                            ctx.csv.load(ctx);
                        }
                    }
                    // Prune repo
                    // user_period.first, user_period.second
                    ctx.repo.prune(new Filter<Activity>() {
                        @Override
                        public boolean OK(Activity activity) {
                            if (activity.date.before(user_period.first) ||
                                    activity.date.after(user_period.second) ||
                                    activity.date.equals(user_period.second))
                                return false;
                            return true;
                        }
                    });
                    Analysis a = new Analysis(ctx.repo);
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

    private List<Pair<Date, Date>> choosePeriods(Context ctx,
                                                 List<Pair<Specifier, List<Specifier>>> periods) {
        if (false && countPeriods(periods) == 1)
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
                Pair<Date, Date> a = MyClub.periodName2Dates(period.name);
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
        List<Pair<Date, Date>> list = new ArrayList<>();
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
            list.add(new Pair<Date, Date>((Date) min_date.clone(), (Date) end.clone()));
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
        Selection() {
        }

        Specifier section;
        List<Specifier> teams;
        List<Pair<Specifier, List<Specifier>>> periods; // Pair<Team, List<Period>>
    }

    private Selection makeSelection(Context ctx, Synchronizer sync, boolean multi) throws Exception {
        Selection selection = new Selection();
        selection.section = DialogBuilder.selectOne(ctx.builder,
                "Select section", sync.listSections(ctx));
        sync.setSection(ctx, selection.section);
        if (multi) {
            selection.teams = DialogBuilder.selectMulti(ctx.builder,
                    "Select teams", sync.listTeams(ctx), true);
        } else {
            selection.teams = new ArrayList<>();
            selection.teams.add(DialogBuilder.selectOne(ctx.builder,
                    "Select team", sync.listTeams(ctx)));
        }
        selection.periods = new ArrayList<>();
        List<Specifier> allperiods = new ArrayList<>();
        for (Specifier team : selection.teams) {
            sync.setTeam(ctx, team);
            Pair<Specifier, List<Specifier>> p = new Pair<>(team,
                    sync.listPeriods(ctx));
            selection.periods.add(p);
            allperiods.addAll(p.second);
        }
        HashSet<Specifier> selected_periods = new HashSet<>();
        if (multi) {
            selected_periods.addAll(DialogBuilder.selectMulti(ctx.builder,
                    "Select periods", allperiods, true));
        } else {
            selected_periods.add(DialogBuilder.selectOne(ctx.builder,
                    "Select period", allperiods));
        }

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

    void set(Context ctx, Specifier section, Specifier team, Specifier period) throws Exception {
    }

    private void setPlanSelection(Context ctx) throws Exception{
        List<Specifier> cwd = ctx.myclub.GetCurrent(ctx);
        if (cwd.size() == 3) {
            // we have FQPN see if we should reuse it...
            Specifier keep = new Specifier(cwd.get(0).name + "/" + cwd.get(1).name + "/" + cwd.get(2).name);
            Specifier change = new Specifier("New selection");
            List<Specifier> choices = new ArrayList<>();
            choices.add(keep);
            choices.add(change);
            Specifier value = DialogBuilder.selectOne(ctx.builder, "Which team?", choices);
            if (value != keep) {
                cwd.clear();
            }
        }
        if (cwd.size() != 3) {
            Selection selection = makeSelection(ctx, ctx.myclub, false);
            set(ctx, selection.section, selection.teams.get(0), selection.periods.get(0).second.get(0));
            ctx.prop.store(new FileOutputStream("config.properties"), null);
        } else {
            ctx.csv.set(ctx, cwd.get(0), cwd.get(1), cwd.get(2));
        }
    }

    private void plan() throws Exception {
        boolean sync = false;
        ctx.myclub.init(ctx.prop);
        if (sync) {
            ctx.myclub.login(ctx.prop, ctx.builder);
            ctx.myclub.setupClub(ctx, ctx.prop, ctx.builder);
        }
        setPlanSelection(ctx);
        ctx.csv.load(ctx);

        sync = false;
        if (sync) {
            // ctx.myclub.set(ctx, ctx.csv);
            ctx.myclub.load(ctx);
        }
        new TeamBuilder(ctx.repo).planGames(ctx);

    }
}

package org.oreland.teambuilder;

import org.oreland.teambuilder.sync.Synchronizer;
import org.oreland.teambuilder.sync.Synchronizer.Specifier;
import org.oreland.teambuilder.ui.Dialog;
import org.oreland.teambuilder.ui.DialogBuilder;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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
    }

    private void statistics() throws Exception {
        Selection selection = makeSelection(ctx, ctx.csv);
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
                    System.out.println("team: " + period.first.name + ", period: " + p.name);
                    period.second.add(p);
                }
            }
        }

        return selection;
    }
}

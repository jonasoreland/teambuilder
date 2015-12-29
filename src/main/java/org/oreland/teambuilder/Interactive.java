package org.oreland.teambuilder;

import org.oreland.teambuilder.sync.Synchronizer;
import org.oreland.teambuilder.sync.Synchronizer.Specifier;
import org.oreland.teambuilder.ui.Dialog;
import org.oreland.teambuilder.ui.DialogBuilder;

import java.util.ArrayList;
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

    private void synchronize() {
    }

    private void statistics() throws Exception {
        Specifier section = DialogBuilder.selectOne(ctx.builder,
                "Select section", ctx.csv.listSections(ctx));
        ctx.csv.setSection(ctx, section);
        List<Specifier> teams = DialogBuilder.selectMulti(ctx.builder,
                "Select teams", ctx.csv.listTeams(ctx), true);
        List<Specifier> allperiods = new ArrayList<>();
        for (Specifier team : teams) {
            ctx.csv.setTeam(ctx, team);
            allperiods.addAll(ctx.csv.listPeriods(ctx));
        }
        List<Specifier> periods = DialogBuilder.selectMulti(ctx.builder,
                "Select periods", allperiods, true);
    }
}

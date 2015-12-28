package org.oreland.teambuilder;

import org.oreland.teambuilder.sync.Synchronizer;
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

    public void run() {
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

    private void statistics() {
        Synchronizer.Specifier section = DialogBuilder.selectOne(ctx.builder,
                "Select section", ctx.csv.listSections(ctx));
    }
}

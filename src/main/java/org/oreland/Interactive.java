package org.oreland;

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
            DialogBuilder builder = new DialogBuilder();
            builder.setQuestion("Choose function");
            List<String> choices = new ArrayList<>();
            choices.add("Quit");
            choices.add("Statistics");
            builder.setChoices(choices);
            Dialog.Result result = builder.build().show();
            switch (result.intResult) {
                case 1:
                    return;
                case 2:
                    statistics();
                    break;
            }
        }
    }

    public void statistics() {
    }
}

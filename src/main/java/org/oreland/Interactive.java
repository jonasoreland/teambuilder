package org.oreland;

import org.oreland.csv.CsvLoader;
import org.oreland.db.Repository;
import org.oreland.sync.MyClub;
import org.oreland.ui.Dialog;
import org.oreland.ui.DialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by jonas on 12/6/15.
 */
public class Interactive {
    Properties prop;
    Repository repo;
    MyClub myclub;
    CsvLoader csv;
    public Interactive(Properties prop, Repository repo, MyClub myclub, CsvLoader csv) {
        this.prop = prop;
        this.repo = repo;
        this.myclub = myclub;
        this.csv = csv;
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

package org.oreland.teambuilder;

import org.oreland.teambuilder.csv.CsvLoader;
import org.oreland.teambuilder.db.Repository;
import org.oreland.teambuilder.sync.MyClub;
import org.oreland.teambuilder.ui.DialogBuilder;

import java.io.File;
import java.util.Properties;

/**
 * Created by jonas on 12/16/15.
 */
public class Context {

    public final String wd;

    public Context() {
        wd = new File(".").getAbsolutePath();
    }

    public final DialogBuilder builder = new DialogBuilder();
    public final Properties prop = new Properties();
    public final Repository repo = new Repository();
    public final MyClub myclub = new MyClub();
    public final CsvLoader csv = new CsvLoader();
}

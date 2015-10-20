package org.oreland;


import org.oreland.analysis.Analysis;
import org.oreland.csv.CsvLoader;
import org.oreland.db.Repository;
import org.oreland.sync.MyClub;
import org.oreland.ui.DialogBuilder;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class TeamBuilder {
    public static void main(String args[]) {
        boolean sync = true;
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.startsWith("sync=")) {
              sync = Boolean.parseBoolean(s.substring(s.indexOf('=')));
            }
        }
        System.out.println("Hello world, sync="+sync);
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("config.properties"));
        } catch (Exception ex) {
        }
        Repository repo = new Repository();
        MyClub myclub = new MyClub();
        CsvLoader csv = new CsvLoader();
        try {
            // first load from file
            csv.load(repo);

            // then load from web
            if (sync) {
                myclub.init(prop);
                myclub.setup(prop, new DialogBuilder());
                prop.store(new FileOutputStream("config.properties"), null);
                myclub.loadPlayers(repo);
                myclub.loadActivities(repo);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Analysis analysis = new Analysis(repo);
        analysis.report();

        // then save to file
        try {
            csv.save(repo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

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
        boolean load = true;
        boolean sync = true;
        boolean save = true;
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.startsWith("load=")) {
              load = Boolean.parseBoolean(s.substring(s.indexOf('=')+1));
            } else if (s.startsWith("sync=")) {
              sync = Boolean.parseBoolean(s.substring(s.indexOf('=')+1));
            } else if (s.startsWith("save=")) {
              save = Boolean.parseBoolean(s.substring(s.indexOf('=')+1));
            } else  if (s.startsWith("resync=")) {
              boolean resync = Boolean.parseBoolean(s.substring(s.indexOf('=')+1));
              if (resync) {
                sync = true;
                load = false;
                save = true;
              }
            }
        }
        System.out.println("Hello world, load="+load+",sync="+sync+",save="+save);
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
            if (load) {
                csv.load(repo);
            }

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
        if (save) {
            try {
                csv.save(repo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

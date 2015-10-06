package org.oreland;


import org.oreland.csv.CsvLoader;
import org.oreland.db.Repository;
import org.oreland.sync.MyClub;
import org.oreland.ui.DialogBuilder;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class TeamBuilder {
    public static void main (String args[]) {
        System.out.println("Hello world");
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("config.properties"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Repository repo = new Repository();
        MyClub myclub = new MyClub();
        CsvLoader csv = new CsvLoader();
        try {
            // first load from file
            csv.load(repo);

            // then load from web
            myclub.init(prop);
            myclub.setup(prop, new DialogBuilder());
            prop.store(new FileOutputStream("config.properties"), null);
            myclub.loadActivities(repo);

            // then save to file
            csv.save(repo);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

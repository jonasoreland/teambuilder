package org.oreland;


import org.oreland.sync.MyClub;
import org.oreland.sync.Synchronizer;
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
        MyClub myclub = new MyClub();
        try {
            myclub.init(prop);
            myclub.setup(prop, new DialogBuilder());
            prop.store(new FileOutputStream("config.properties"), null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

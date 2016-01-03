package org.oreland.teambuilder;

import org.oreland.teambuilder.analysis.Analysis;
import org.oreland.teambuilder.ui.DialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Created by jonas on 12/16/15.
 */
public class Main {
    public static void main(String args[]) throws Exception {
        boolean load = false;
        boolean sync = false;
        boolean save = false;
        boolean plan = false;
        boolean analyze = false;
        boolean interactive = true;
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.startsWith("load=")) {
                load = Boolean.parseBoolean(s.substring(s.indexOf('=') + 1));
            } else if (s.startsWith("sync=")) {
                sync = Boolean.parseBoolean(s.substring(s.indexOf('=') + 1));
            } else if (s.startsWith("save=")) {
                save = Boolean.parseBoolean(s.substring(s.indexOf('=') + 1));
            } else if (s.startsWith("plan=")) {
                plan = Boolean.parseBoolean(s.substring(s.indexOf('=') + 1));
            } else if (s.startsWith("analyze=")) {
                analyze = Boolean.parseBoolean(s.substring(s.indexOf('=') + 1));
            } else if (s.startsWith("interactive=")) {
                interactive = Boolean.parseBoolean(s.substring(s.indexOf('=') + 1));
            } else if (s.startsWith("resync=")) {
                boolean resync = Boolean.parseBoolean(s.substring(s.indexOf('=') + 1));
                if (resync) {
                    sync = true;
                    load = false;
                    save = true;
                }
            }
        }
        System.out.println("Hello world, load=" + load + ",sync=" + sync + ",save=" + save +",plan=" + plan);
        Context ctx = new Context();

        try {
            if (new File("config.properties").exists())
                ctx.prop.load(new FileInputStream("config.properties"));
        } catch (Exception ex) {
        }

        try {
            // first load from file
            if (load) {
                ctx.csv.load(ctx);
            }

            // then load from web
            if (sync) {
                ctx.myclub.init(ctx.prop);
                ctx.myclub.setup(ctx, ctx.prop, new DialogBuilder());
                ctx.prop.store(new FileOutputStream("config.properties"), null);
                ctx.myclub.loadPlayers(ctx);
                ctx.myclub.loadActivities(ctx);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (analyze) {
            Analysis analysis = new Analysis(ctx.repo);
            analysis.report(null, null, null);
        }

        if (plan) {
            TeamBuilder builder = new TeamBuilder(ctx.repo);
            builder.plan();
        }

        // then save to file
        if (save) {
            try {
                ctx.csv.save(ctx);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (interactive) {
            new Interactive(ctx).run();
        }
    }
}

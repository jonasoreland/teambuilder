package org.oreland;


import org.oreland.analysis.Analysis;
import org.oreland.csv.CsvLoader;
import org.oreland.db.Repository;
import org.oreland.entity.Activity;
import org.oreland.entity.Level;
import org.oreland.entity.Player;
import org.oreland.entity.TargetLevel;
import org.oreland.sync.MyClub;
import org.oreland.ui.Dialog;
import org.oreland.ui.DialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class TeamBuilder {
    public static void main(String args[]) {
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
        Properties prop = new Properties();
        Repository repo = new Repository();
        MyClub myclub = new MyClub();
        CsvLoader csv = new CsvLoader();

        try {
            if (new File("config.properties").exists())
                prop.load(new FileInputStream("config.properties"));
        } catch (Exception ex) {
        }

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

        if (analyze) {
            Analysis analysis = new Analysis(repo);
            analysis.report();
        }

        if (plan) {
            TeamBuilder builder = new TeamBuilder(repo);
            builder.plan();
        }

        // then save to file
        if (save) {
            try {
                csv.save(repo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (interactive) {
            new Interactive(prop, repo, myclub, csv).run();
        }
    }

    final Repository repo;

    public TeamBuilder(Repository repo) {
        this.repo = repo.clone();
    }

    public void plan() {
        // 1. Select games to plan
        List<Activity> games = selectGames();
        for (Activity act : games) {
            act.invitations.clear();
        }
        // 2. Determine availability
        Activity request = selectRequest(games);
        setResponses(request, games);
        // 3. Set no of paritcipants per game
        setParticipants(games);
        // 3. Compute teams
        computeTeams(games);
    }

    private void setStartOfWeek(Calendar cal) {
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.clear(Calendar.MINUTE);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
    }

    private List<Activity> selectGames() {
        Calendar cal = Calendar.getInstance();
        setStartOfWeek(cal);

        Date startOfWeek = cal.getTime();
        cal.add(Calendar.DATE, 7);
        Date endOfWeek = cal.getTime();

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        List<Activity> candidate_games = repo.selectActivities(startOfWeek, endOfWeek, Activity.Type.GAME);
        Collections.sort(candidate_games, new Comparator<Activity>() {
            @Override
            public int compare(Activity a1, Activity a2) {
                int res = a1.date.compareTo(a2.date);
                if (res != 0)
                    return res;
                if (a1.time != null && a2.time != null) {
                    return a1.time.compareTo(a2.time);
                }
                return 0;
            }
        });
        List<String> choices = new ArrayList<>();
        for (Activity act : candidate_games) {
            choices.add(formatter.format(act.date) + " " + act.time + " - " + act.title + ", " + act.level.toString());
        }
        DialogBuilder builder = new DialogBuilder();
        builder.setQuestion("Choose games to plan (" + formatter.format(startOfWeek) + " - " + formatter.format(endOfWeek) + "): ");
        builder.setMultiChoices(choices);
        Dialog.Result result = builder.build().show();
        List<Activity> games = new ArrayList<>();
        for (Integer i : result.intResults) {
            games.add(candidate_games.get(i - 1));
        }
        return games;
    }

    private Activity selectRequest(List<Activity> games) {
        Calendar cal = Calendar.getInstance();
        Date minDate = cal.getTime(), maxDate = cal.getTime();
        for (Activity act : games) {
            if (act.date.before(minDate))
                minDate = act.date;
            if (act.date.after(maxDate))
                maxDate = act.date;
        }
        cal.setTime(minDate);
        setStartOfWeek(cal);
        Date startOfWeek = cal.getTime();
        cal.setTime(maxDate);
        setStartOfWeek(cal);
        cal.add(Calendar.DATE, 7);
        Date endOfWeek = cal.getTime();

        List<Activity> candidate_requests = repo.selectActivities(startOfWeek, endOfWeek, Activity.Type.REQUEST);
        List<String> choices = new ArrayList<>();
        for (Activity act : candidate_requests) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
            choices.add(formatter.format(act.date) + " " + " - " + act.title);
        }
        DialogBuilder builder = new DialogBuilder();
        builder.setQuestion("Choose request for availability: ");
        builder.setChoices(choices);
        Dialog.Result result = builder.build().show();
        return candidate_requests.get(result.intResult - 1);
    }

    void setResponses(Activity request, List<Activity> games) {
        int yes = 0;
        for (Activity.Invitation invitation : request.invitations) {
            if (invitation.player.type != Player.Type.PLAYER)
                continue;
            if (invitation.response == Activity.Response.YES)
                yes++;
        }
        System.out.println("Found " + yes + " positive replies!");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        List<String> choices = new ArrayList<>();
        for (Activity act : games) {
            choices.add(formatter.format(act.date) + " " + act.time + " - " + act.title + ", " + act.level.toString());
        }
        int cnt = 1;
        DialogBuilder builder = new DialogBuilder();
        builder.setMultiChoices(choices);
        for (Activity.Invitation invitation : request.invitations) {
            if (invitation.player.type != Player.Type.PLAYER)
                continue;
            if (invitation.response == Activity.Response.YES) {
                builder.setQuestion(Integer.toString(cnt) + "/" + yes + " which games can " + invitation.player.first_name + " " + invitation.player.last_name + " play ?" +
                        "\nResponse: " + invitation.response_comment);
                Dialog.Result result = builder.build().show();
                for (Integer i : result.intResults) {
                    Activity.Invitation inv = new Activity.Invitation();
                    inv.player = invitation.player;
                    inv.response = Activity.Response.YES;
                    games.get(i - 1).invitations.add(inv);
                }
                cnt++;
            }
        }
    }

    void setParticipants(List<Activity> games) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        for (Activity act : games) {
            DialogBuilder builder = new DialogBuilder();
            builder.setQuestion("Choose no of players for game\n- " + formatter.format(act.date) + " " +
                    act.time + " - " + act.title + ", " + act.level.toString());
            builder.setRange(1, 99);
            Dialog.Result result = builder.build().show();
            for (int i = 0; i < result.intResult; i++) {
                act.participants.add(new Activity.Participant());
            }
        }
    }

    Level getOptimalNextGame(Player p) {
        TargetLevel played = new TargetLevel();
        for (Activity act : p.games_played) {
            played.getOrCreate(act.level).count++;
        }
        return null;
    }

    void computeTeams(List<Activity> games) {
    }
}

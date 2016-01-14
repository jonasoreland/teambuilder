package org.oreland.teambuilder;


import org.oreland.teambuilder.db.Repository;
import org.oreland.teambuilder.entity.Activity;
import org.oreland.teambuilder.entity.Level;
import org.oreland.teambuilder.entity.Player;
import org.oreland.teambuilder.entity.TargetLevel;
import org.oreland.teambuilder.ui.Dialog;
import org.oreland.teambuilder.ui.DialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

class TeamBuilder {

    private final Repository repo;
    private TargetLevel playersPerGame;
    private TargetLevel gamesPerLevel;
    private List<Pair<Level, TargetLevel>> distribution;

    public TeamBuilder(Repository repo) {
        this.repo = repo.clone();
    }

    public void planSeason(Context ctx) throws Exception {
        // 1. compute how to construct teams
        computeDistribution();
    }

    public void planGames(Context ctx) throws Exception {
        // 1. compute how to construct teams
        computeDistribution();

        // 1. Select games to plan
        List<Activity> games = selectGames();
        for (Activity act : games) {
            act.invitations.clear();
        }
        // 2. Determine availability
        Activity request = selectRequest(games);
        setResponses(request, games);
        // 3. Set no of participants per game
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

    private void setResponses(Activity request, List<Activity> games) {
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

    private void setParticipants(List<Activity> games) {
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

    TargetLevel getPlayersPerLevel() throws Exception {
        Player target = null;
        for (Player p : repo.getPlayers()) {
            if (p.type != Player.Type.LEADER)
                continue;
            if (p.target_level != null) {
                if (target == null)
                    target = p;
                else if (!target.target_level.equal(p.target_level)) {
                    System.out.println("Conflicting counts!");
                    System.out.println(target + " - " + target.target_level);
                    System.out.println(p + " - " + p.target_level);
                    throw new Exception("Error!");
                }
            }
        }
        if (target == null) {
            System.out.println("Could not find players per level!");
            System.out.println("Please set this a targetLevel on a LEADER");
            throw new Exception("Error!");
        }
        return target.target_level;
    }

    TargetLevel countGamesPerLevel() throws Exception {
        TargetLevel gamesPerLevel = new TargetLevel();
        for (Activity a : repo.getActivities()) {
            if (a.type != Activity.Type.GAME)
                continue;
            if (a.level == null) {
                System.out.println("Found game without level! " + a);
                throw new Exception("Error");
            }
            gamesPerLevel.getOrCreate(a.level).count++;
        }
        return gamesPerLevel;
    }

    TargetLevel computeAppearancesPerLevel(TargetLevel playersPerGame, TargetLevel gamesPerLevel) {
        TargetLevel playersPerLevel = new TargetLevel();
        for (TargetLevel.Distribution d1 : playersPerGame.distribution) {
            TargetLevel.Distribution d2 = gamesPerLevel.getOrCreate(d1.level);
            TargetLevel.Distribution new_d = playersPerLevel.getOrCreate(d1.level);
            new_d.count = d1.count * d2.count;
        }
        return playersPerLevel;
    }

    List<TargetLevel> getPlayerLevels() {
        List<TargetLevel> levels = new ArrayList<>();
        for (Player p : repo.getPlayers()) {
            if (p.type != Player.Type.PLAYER)
                continue;
            if (p.target_level != null) {
                levels.add(new TargetLevel(p.target_level));
            } else {
                System.out.println(p + " has no target level!");
            }
        }
        return levels;
    }

    private void computeDistribution() throws Exception {
        // AJ35:AJ39
        playersPerGame = getPlayersPerLevel();
        System.out.println("playersPerGame: " + playersPerGame);
        // AI35:AI39
        gamesPerLevel = countGamesPerLevel();
        System.out.println("gamesPerLevel: " + gamesPerLevel);
        // AL35:AL39
        TargetLevel appearancesPerLevel = computeAppearancesPerLevel(playersPerGame, gamesPerLevel);

        // AI40
        double totalAppearances = 0; // total "appearances"
        for (TargetLevel.Distribution d : appearancesPerLevel.distribution) {
            totalAppearances += d.count;
        }

        List<TargetLevel> playerLevels = normalize(getPlayerLevels());

        // D25
        double sumGamesPerWeek = playerLevels.size(); // TODO

        // U4:Y24
        for (TargetLevel l : playerLevels) {
            for (TargetLevel.Distribution d : l.distribution) {
                d.count *= totalAppearances;
                d.count /= sumGamesPerWeek;
            }
        }

        // U25:Y25
        TargetLevel sumLevel = new TargetLevel();
        for (TargetLevel l : playerLevels) {
            for (TargetLevel.Distribution d : l.distribution) {
                sumLevel.getOrCreate(d.level).count += d.count;
            }
        }

        // AA4:AE4
        TargetLevel norm = new TargetLevel(appearancesPerLevel);
        for (TargetLevel.Distribution d : norm.distribution) {
            TargetLevel.Distribution d2 = sumLevel.get(d.level);
            if (d2 == null || d2.count == 0) {
                d.count = 0;
            } else {
                d.count /= d2.count;
            }
        }

        // AG4:AL24
        for (TargetLevel l : playerLevels) {
            for (TargetLevel.Distribution d : l.distribution) {
                d.count = Math.round(d.count * norm.getOrCreate(d.level).count);
                d.count = Math.min(d.count, gamesPerLevel.getOrCreate(d.level).count);
            }
        }

        List<TargetLevel> orgPlayerLevels = getPlayerLevels();
        distribution = new ArrayList<>();
        for (Level typeOfGame : repo.getLevels()) {
            TargetLevel t = new TargetLevel();
            for (Level typeOfPlayer : repo.getLevels()) {
                TargetLevel.Distribution d = t.getOrCreate(typeOfPlayer);
                // Scan all players
                for (int i = 0; i < orgPlayerLevels.size(); i++) {
                    TargetLevel org = orgPlayerLevels.get(i);
                    TargetLevel lev = playerLevels.get(i);
                    if (org.getBestMatchLevel() == typeOfPlayer) {
                        if (lev.get(typeOfGame) == null)
                            continue;
                        d.count += lev.get(typeOfGame).count;
                    }
                }
                d.count /= gamesPerLevel.get(typeOfGame).count;
                d.count = Math.round(d.count);
            }
            distribution.add(new Pair<>(typeOfGame, t));
        }
        System.out.println("Player distribution:");
        for (Pair<Level, TargetLevel> d : distribution) {
            System.out.println(d.first + " : " + d.second);
        }
    }

    List<TargetLevel> normalize(List<TargetLevel> playerLevels) {
        for (TargetLevel l : playerLevels) {
            l.normalize();
        }
        return playerLevels;
    }

    Level getOptimalNextGame(Player p) {
        TargetLevel played = new TargetLevel();
        for (Activity act : p.games_played) {
            played.getOrCreate(act.level).count++;
        }
        return null;
    }

    private void computeTeams(List<Activity> games) throws Exception {
    }
}

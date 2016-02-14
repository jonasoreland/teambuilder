package org.oreland.teambuilder;

import org.oreland.teambuilder.db.Filter;
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
import java.util.HashMap;
import java.util.List;

class TeamBuilder {

    class ScoredPlayer {
        Player player;
        TargetLevel orgTargetLevel = new TargetLevel();       // org target games per level (from MyClub)
        TargetLevel targetGamesPerLevel = new TargetLevel();  // target games per level
        TargetLevel gamesPlayed = new TargetLevel();          // games played per level

        int trained_last_period; // 1) did player train sufficiently last period, 1 = yes
        int played_last_period;  // 2) did player play sufficiently games last periond, 1 = no
        Level optimalNextGame;   // 3) what is optimal next level for player

        ScoredPlayer(Player p) {
            this.player = p;

            for (Activity g : p.games_played) {
                if (g.type == Activity.Type.TRAINING)
                    continue;
                if (g.type == Activity.Type.REQUEST)
                    continue;
                if (g.level != null) {
                    gamesPlayed.getOrCreate(g.level).count++;
                }
            }
            orgTargetLevel = new TargetLevel(p.target_level);
        }

        public int getGamesPlayed() {
            int sum = 0;
            for (TargetLevel.Distribution d : gamesPlayed.distribution) {
                sum += d.count;
            }
            return sum;
        }
    };

    private final Repository repo;
    private TargetLevel playersPerGame;
    private TargetLevel gamesPerLevel;
    private List<Pair<Level, TargetLevel>> distribution;
    private List<ScoredPlayer> scoredPlayers;

    public TeamBuilder(Repository repo) {
        this.repo = repo.clone();
    }

    public void planAbstractSeason(Context ctx) throws Exception {
        // 1. remove all games from this season
        this.repo.prune(new Filter<Activity>() {
            @Override
            public boolean OK(Activity o) {
                return false;
            }
        });
        // 2. Determine number of/and create games per level
        askGamesPerLevel();

        // 3. compute how to construct teams
        computeDistribution();

        for (ScoredPlayer p : getScoredPlayers()) {
            double underflow = 0;
            for (Level l : repo.getLevelsReverse()) {
                // add games per level upp until target

                // player should not play any such games
                if (p.targetGamesPerLevel.get(l) == null)
                    continue;

                double played = p.gamesPlayed.getOrCreate(l).count;
                double target = p.targetGamesPerLevel.get(l).count;
                for (double i = played; i < target + underflow; i++) {
                    Activity a = findGameForPlayer(p.player, l);
                    if (a == null) {
                        underflow = target + underflow - i;
                        break;
                    }
                    repo.addParticipant(a, p.player);
                }
            }
        }

        // 4. print teams
        printTeams(repo.getActivities());

        // 5. print stats
        for (ScoredPlayer p : getScoredPlayers()) {
            System.out.println(p.player + ", played: " + p.player.games_level + ", target: " + p.player.target_level +
            ", computed: " + p.targetGamesPerLevel);
        }
    }

    private Activity findGameForPlayer(Player player, Level l) {
        List<Activity> list = new ArrayList<>();
        for (Activity a : repo.getActivities()) {
            if (a.level != l)
                continue;
            if (a.hasParticipant(player))
                continue;
            list.add(a);
        }

        // pick games with least players
        Activity game = null;
        for (Activity a : list) {
            if (game == null)
                game = a;
            if (a.participants.size() < game.participants.size())
                game = a;
        }

        return game;
    }

    private void printTeams(Iterable<Activity> list) {
        for (Activity a : list) {
            System.out.println("*** " + a + ", " + a.level + ", " + a.getDistribution());
            for (Activity.Participant p : a.participants) {
                System.out.println(p.player + ", " + p.player.target_level + ", played: " + p.player.games_level + " => next: " +
                        p.player.target_level.getNextGameLevel(p.player.games_level));
            }
            System.out.println("");
        }
    }

    private void askGamesPerLevel() {
        Calendar cal = Calendar.getInstance();
        cal.clear(Calendar.HOUR);
        cal.clear(Calendar.MINUTE);
        cal.clear(Calendar.SECOND);
        Date today = cal.getTime();

        int count = 0;
        for (Level l : repo.getLevels()) {
            DialogBuilder builder = new DialogBuilder();
            builder.setQuestion("No of games with level: " + l.toString());
            builder.setRange(0, 100);
            Dialog.Result result = builder.build().show();
            for (int i = 0; i < result.intResult; i++) {
                Activity a = new Activity();
                a.id = Integer.toString(++count) + ":" + l.toString();
                a.level = l;
                a.type = Activity.Type.GAME;
                a.date = today;
                repo.add(a);
                cal.add(Calendar.DAY_OF_YEAR, 1);
                today = cal.getTime();
            }
        }
    }

    public void planWeekendGames(Context ctx) throws Exception {
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
        // 4. Print teams
        printTeams(games);
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

    List<ScoredPlayer> getScoredPlayers() {
        if (scoredPlayers != null) {
            return scoredPlayers;
        }
        scoredPlayers = new ArrayList<>();
        for (Player p : repo.getPlayers()) {
            if (p.type != Player.Type.PLAYER)
                continue;
            if (p.target_level != null) {
                scoredPlayers.add(new ScoredPlayer(p));
            } else {
                System.out.println(p + " has no target level!");
            }
        }
        Collections.sort(scoredPlayers, new Comparator<ScoredPlayer>() {
            @Override
            public int compare(ScoredPlayer p1, ScoredPlayer p2) {
                int c1 = repo.getLevelIndex(p1.orgTargetLevel.getBestMatchLevel());
                int c2 = repo.getLevelIndex(p2.orgTargetLevel.getBestMatchLevel());
                return c2 - c1;
            }
        });
        return scoredPlayers;
    }

    private void computeDistribution() throws Exception {
        // AJ35:AJ39
        playersPerGame = getPlayersPerLevel();
        System.out.println("playersPerGame: " + playersPerGame);
        // AI35:AI39
        gamesPerLevel = countGamesPerLevel();
        System.out.println("targetGamesPerLevel: " + gamesPerLevel);
        // AL35:AL39
        TargetLevel appearancesPerLevel = computeAppearancesPerLevel(playersPerGame, gamesPerLevel);

        // AI40
        double totalAppearances = 0; // total "appearances"
        for (TargetLevel.Distribution d : appearancesPerLevel.distribution) {
            totalAppearances += d.count;
        }

        List<ScoredPlayer> playerLevels = normalize(getScoredPlayers());

        // D25
        double sumGamesPerWeek = playerLevels.size(); // TODO

        // U4:Y24
        for (ScoredPlayer p : playerLevels) {
            p.targetGamesPerLevel = new TargetLevel(p.orgTargetLevel);
            for (TargetLevel.Distribution d : p.targetGamesPerLevel.distribution) {
                d.count *= totalAppearances;
                d.count /= sumGamesPerWeek;
            }
        }

        // U25:Y25
        TargetLevel sumLevel = new TargetLevel();
        for (ScoredPlayer p : playerLevels) {
            for (TargetLevel.Distribution d : p.targetGamesPerLevel.distribution) {
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
        for (ScoredPlayer p : playerLevels) {
            for (TargetLevel.Distribution d : p.targetGamesPerLevel.distribution) {
                d.count = Math.round(d.count * norm.getOrCreate(d.level).count);
                d.count = Math.min(d.count, gamesPerLevel.getOrCreate(d.level).count);
            }
        }

        distribution = new ArrayList<>();
        for (Level typeOfGame : repo.getLevels()) {
            TargetLevel t = new TargetLevel();
            for (Level typeOfPlayer : repo.getLevels()) {
                TargetLevel.Distribution d = t.getOrCreate(typeOfPlayer);
                // Scan all players
                for (ScoredPlayer p : playerLevels) {
                    if (p.orgTargetLevel.getBestMatchLevel() == typeOfPlayer) {
                        if (p.targetGamesPerLevel.get(typeOfGame) == null)
                            continue;
                        d.count += p.targetGamesPerLevel.get(typeOfGame).count;
                    }
                }

                if (gamesPerLevel.get(typeOfGame) != null)
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

    TargetLevel getGameDistribution(Level levelOfGame) {
        for (Pair<Level, TargetLevel> d : distribution) {
            if (d.first == levelOfGame)
                return d.second;
        }
        return null;
    }

    List<ScoredPlayer> normalize(List<ScoredPlayer> playerLevels) {
        for (ScoredPlayer l : playerLevels) {
            l.orgTargetLevel.normalize();
        }
        return playerLevels;
    }

    private void computeTeams(List<Activity> games) throws Exception {
        // split players according to Level
        HashMap<Level, List<Player>> playersPerLevel = splitPlayersPerLevel();

        // sort games according to Level, start with "hardest"
        Collections.sort(games, new Comparator<Activity>() {
            @Override
            public int compare(Activity a1, Activity a2) {
                return - a1.level.compare(repo, a2.level);
            }
        });

        List<Activity> complete = new ArrayList<>();
        for (Activity a : games) {
            int underflow = 0;
            TargetLevel players = getGameDistribution(a.level);
            for (Level l : repo.getLevelsReverse()) {
                // try to allocate #players + underflow
                int count = (int) (players.get(l).count + underflow);
                List<Player> playersOfLevel = playersPerLevel.get(l);
                if (count > playersOfLevel.size()) {
                    underflow = count - playersOfLevel.size();
                    count = playersOfLevel.size();
                }
                List<Player> chosen = pickPlayersForGame(a.level, playersOfLevel, count, complete);
                for (Player p : chosen) {
                    repo.addParticipant(a, p);
                }
            }
            complete.add(a);
        }
    }

    private List<Player> pickPlayersForGame(Level l, List<Player> playersOfLevel, int count, List<Activity> games) {
        List<Player> list = new ArrayList<>();
        List<ScoredPlayer> sort = new ArrayList<>();
        sort.addAll(scoredPlayers);
        Collections.sort(sort, new Comparator<ScoredPlayer>() {
            @Override
            public int compare(ScoredPlayer p1, ScoredPlayer p2) {
                if (p1.trained_last_period != p2.trained_last_period) {
                    String s = null;
                    s.hashCode();
                    return p2.trained_last_period - p1.trained_last_period;
                }
                if (p1.played_last_period != p2.played_last_period) {
                    String s = null;
                    s.hashCode();
                    return p2.played_last_period - p1.played_last_period;
                }

                if (p1.optimalNextGame != p2.optimalNextGame) {
                    return repo.getLevelIndex(p2.optimalNextGame) - repo.getLevelIndex(p1.optimalNextGame);
                }
                if (p1.getGamesPlayed() != p2.getGamesPlayed()) {
                    return p1.getGamesPlayed() - p2.getGamesPlayed();
                }

                return 0;
            }
        });
        for (ScoredPlayer sp : sort) {
            if (count == 0)
                break;
            list.add(sp.player);
            count--;
        }
        return list;
    }

    private HashMap<Level, List<Player>> splitPlayersPerLevel() {
        HashMap<Level, List<Player>> list = new HashMap<>();
        for (Level l : repo.getLevels()) {
            list.put(l, new ArrayList<Player>());
        }
        for (Player p : repo.getPlayers()) {
            if (p.type != Player.Type.PLAYER)
                continue;
            if (p.target_level == null)
                continue;
            List<Player> l = list.get(p.target_level.getBestMatchLevel());
            l.add(p);
        }
        return list;
    }
}

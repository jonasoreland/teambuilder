package org.oreland.teambuilder.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.oreland.teambuilder.Context;
import org.oreland.teambuilder.Pair;
import org.oreland.teambuilder.db.Repository;
import org.oreland.teambuilder.entity.Activity;
import org.oreland.teambuilder.entity.Level;
import org.oreland.teambuilder.entity.Player;
import org.oreland.teambuilder.entity.TargetLevel;
import org.oreland.teambuilder.sync.DefaultSynchronizer;
import org.oreland.teambuilder.sync.MyClub;
import org.oreland.teambuilder.sync.Synchronizer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Created by jonas on 10/5/15.
 */
public class CsvLoader extends DefaultSynchronizer implements Synchronizer {

    private String dir;
    private String section;
    private String team;
    private String period;

    private String getBaseDir(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.wd);
        sb.append(File.separatorChar);
        sb.append("csv");
        sb.append(File.separatorChar);
        return sb.toString();
    }

    private void changeDir(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(getBaseDir(ctx));
        sb.append(this.section);
        sb.append(File.separatorChar);
        sb.append(this.team);
        sb.append(File.separatorChar);
        sb.append(this.period);
        dir = sb.toString();
    }

    private String getActivitiesFilename() {
        return dir + "/" + "activities.csv";
    }

    private String getParticipantsFilename() {
        return dir + "/" + "participants.csv";
    }

    private String getInvitationsFilename() {
        return dir + "/" + "invitations.csv";
    }

    private String getPlayersFilename() {
        return dir + "/" + "players.csv";
    }

    private String getLevelsFilename() {
        return dir + "/" + "levels.csv";
    }

    public void load(Context ctx) throws IOException, ParseException {
        changeDir(ctx);
        loadPlayers(ctx.repo);
        loadActivities(ctx.repo);
        loadInvitations(ctx.repo);
        loadParticipants(ctx.repo);
    }

    public void save(Context ctx) throws IOException, ParseException {
        changeDir(ctx);
        new File(dir).mkdirs();
        savePlayers(ctx.repo);
        saveActivities(ctx.repo);
        saveInvitations(ctx.repo);
        saveParticipants(ctx.repo);
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void init(Properties config) {

    }

    @Override
    public void reset() {

    }

    @Override
    public Status connect() {
        return Status.OK;
    }

    @Override
    public void logout() {

    }

    @Override
    public List<Specifier> listSections(Context ctx) {
        List<Specifier> list = new ArrayList<>();
        try {
            String path = getBaseDir(ctx);
            for (Path file : Files.newDirectoryStream(Paths.get(path))) {
                if (new File(file.toString()).isDirectory())
                    list.add(new Specifier(file.getFileName().toString(), file.toString()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void setSection(Context ctx, Specifier section) throws Exception {
        this.section = section.name;
    }

    @Override
    public Specifier getCurrentSection(Context ctx) {
        if (this.section != null)
            return new Specifier(this.section);
        return null;
    }

    @Override
    public void setTeam(Context ctx, Specifier team) throws Exception {
        this.team = team.name;
    }

    @Override
    public Specifier getCurrentTeam(Context ctx) {
        if (this.team != null)
            return new Specifier(this.team);
        return null;
    }

    @Override
    public void setPeriod(Context ctx, Specifier period) throws Exception {
        this.period = period.name;
    }

    @Override
    public Specifier getCurrentPeriod(Context ctx) {
        if (this.period != null)
            return new Specifier(this.period);
        return null;
    }

    @Override
    public List<Specifier> listTeams(Context ctx) {
        List<Specifier> list = new ArrayList<>();
        try {
            String path = getBaseDir(ctx) + section + File.separatorChar;
            for (Path file : Files.newDirectoryStream(Paths.get(path))) {
                if (new File(file.toString()).isDirectory())
                    list.add(new Specifier(file.getFileName().toString(), file.toString()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Specifier> listPeriods(Context ctx) {
        List<Specifier> list = new ArrayList<>();
        try {
            String path = getBaseDir(ctx) + section + File.separatorChar + team + File.separatorChar;
            for (Path file : Files.newDirectoryStream(Paths.get(path))) {
                if (new File(file.toString()).isDirectory())
                    list.add(new Specifier(file.getFileName().toString(), file.toString()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collections.sort(list, new Comparator<Specifier>() {
            public int compare(Specifier s1, Specifier s2) {
                Pair<Date, Date> p1 = MyClub.periodName2Dates(s1.name);
                Pair<Date, Date> p2 = MyClub.periodName2Dates(s2.name);
                return p1.first.compareTo(p2.first);
            }
        });
        return list;
    }

    private void loadActivities(Repository repo) throws ParseException, IOException {
        File f = new File(getActivitiesFilename());
        if (!f.exists()) {
            return;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(
                new FileReader(getActivitiesFilename()));
        for (CSVRecord record : records) {
            Activity g = new Activity();
            g.id = record.get("id");
            g.date = formatter.parse(record.get("date"));
            g.time = record.get("time");
            g.title = record.get("title");
            g.type = Activity.Type.parse(record.get("type"));
            g.level = Level.parse(repo, record.get("level"));
            g.synced = Boolean.parseBoolean(record.get("synced"));
            repo.add(g);
        }
    }

    private void saveActivities(Repository repo) throws IOException {
        final Appendable out = new FileWriter(getActivitiesFilename());
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader("id", "date", "time", "title", "type", "level", "synced").print(out);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        for (Activity game : repo.getActivities()) {
            List<String> rec = new ArrayList<>();
            rec.add(game.id);
            rec.add(formatter.format(game.date));
            rec.add(game.time);
            rec.add(game.title);
            rec.add(game.type.toString());
            if (game.level != null)
                rec.add(game.level.toString());
            else
                rec.add("");
            rec.add(Boolean.toString(game.synced));
            printer.printRecord(rec);
        }
        printer.close();
    }

    private void loadInvitations(Repository repo) throws ParseException, IOException {
        File f = new File(getInvitationsFilename());
        if (!f.exists()) {
            return;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd HH:ss");
        Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(new FileReader(getInvitationsFilename()));
        for (CSVRecord record : records) {
            Activity.Invitation g = new Activity.Invitation();
            String game_id = record.get("game");
            Activity game = repo.getActivity(game_id);
            Player p = new Player(record.get("first_name"), record.get("last_name"));
            p.type = Player.Type.parse(record.get("type"));
            g.player = repo.add(p);
            if (!record.get("invitation_date").isEmpty())
                g.invitation_date = formatter.parse(record.get("invitation_date"));
            g.response = Activity.Response.parse(record.get("response"));
            if (g.response != Activity.Response.NO_RESPONSE) {
                g.response_date = formatter.parse(record.get("response_date"));
                g.response_comment = record.get("response_comment");
            }
            game.invitations.add(g);
        }
    }

    private void saveInvitations(Repository repo) throws ParseException, IOException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd HH:ss");
        final Appendable out = new FileWriter(dir + "/" + "invitations.csv");
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader("game", "first_name", "last_name", "type", "invitation_date", "response", "response_date", "response_comment").print(out);
        for (Pair<Activity, Activity.Invitation> invitation : repo.getInvitations()) {
            List<String> rec = new ArrayList<>();
            rec.add(invitation.first.id);
            rec.add(invitation.second.player.first_name);
            rec.add(invitation.second.player.last_name);
            rec.add(invitation.second.player.type.toString());
            if (invitation.second.invitation_date != null)
                rec.add(formatter.format(invitation.second.invitation_date));
            else
                rec.add("");
            if (invitation.second.response != null &&
                    invitation.second.response != Activity.Response.NO_RESPONSE) {
                rec.add(invitation.second.response.toString());
                rec.add(formatter.format(invitation.second.response_date));
                rec.add(invitation.second.response_comment);
            } else {
                rec.add("");
                rec.add("");
                rec.add("");
            }
            printer.printRecord(rec);
        }
        printer.close();
    }

    private void loadParticipants(Repository repo) throws ParseException, IOException {
        File f = new File(getParticipantsFilename());
        if (!f.exists()) {
            return;
        }
        Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(new FileReader(getParticipantsFilename()));
        for (CSVRecord record : records) {
            Activity.Participant g = new Activity.Participant();
            String game_id = record.get("game");
            Activity game = repo.getActivity(game_id);
            Player p = new Player(record.get("first_name"), record.get("last_name"));
            p.type = Player.Type.parse(record.get("type"));
            repo.addParticipant(game, repo.add(p));
        }
    }

    private void saveParticipants(Repository repo) throws ParseException, IOException {
        final Appendable out = new FileWriter(getParticipantsFilename());
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader("game", "first_name", "last_name", "type").print(out);
        for (Pair<Activity, Activity.Participant> participant : repo.getParticipants()) {
            List<String> rec = new ArrayList<>();
            rec.add(participant.first.id);
            rec.add(participant.second.player.first_name);
            rec.add(participant.second.player.last_name);
            rec.add(participant.second.player.type.toString());
            printer.printRecord(rec);
        }
        printer.close();
    }

    private void loadLevels(Repository repo) throws IOException {
        File f = new File(getLevelsFilename());
        if (!f.exists()) {
            return;
        }
        Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(new FileReader(getLevelsFilename()));
        for (CSVRecord record : records) {
            Level l = new Level();
            l.name = record.get("level");
            repo.addLevel(l);
        }
    }

    private void saveLevels(Repository repo) throws IOException {
        final Appendable out = new FileWriter(getLevelsFilename());
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader("level").print(out);
        for (Level l : repo.getLevels()) {
            List<String> rec = new ArrayList<>();
            rec.add(l.name);
            printer.printRecord(rec);
        }
        printer.close();
    }

    private void loadPlayers(Repository repo) throws IOException, ParseException {
        File f = new File(getPlayersFilename());
        if (!f.exists()) {
            return;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(
                new FileReader(f.getAbsoluteFile()));
        for (CSVRecord record : records) {
            Player p = new Player(record.get("first_name"), record.get("last_name"));
            p.type = Player.Type.parse(record.get("type"));
            p.guest = Boolean.parseBoolean(record.get("guest"));
            p = repo.add(p);
            Date d = formatter.parse(record.get("date"));
            System.out.println(p + " - " + record.get("target"));
            TargetLevel level = TargetLevel.parseJson(repo, record.get("target"));
            repo.addTarget(p, level, d);
        }
    }

    private void savePlayers(Repository repo) throws IOException {
        final Appendable out = new FileWriter(getPlayersFilename());
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader("date", "first_name", "last_name", "type", "guest", "target").print(out);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        Iterator<Player> players = repo.getPlayers().iterator();
        while (players.hasNext()) {
            Player p = players.next();
            if (p.level_history.isEmpty()) {
                List<String> rec = new ArrayList<>();
                rec.add(formatter.format(Calendar.getInstance().getTime()));
                rec.add(p.first_name);
                rec.add(p.last_name);
                rec.add(p.type.toString());
                rec.add("" + p.guest);
                rec.add("");
                printer.printRecord(rec);
            } else {
                List<Player.LevelHistoryEntry> list = new ArrayList<>();
                list.addAll(p.level_history);
                Collections.sort(list, new Comparator<Player.LevelHistoryEntry>() {
                    @Override
                    public int compare(Player.LevelHistoryEntry p1, Player.LevelHistoryEntry p2) {
                        return p1.date.compareTo(p2.date);
                    }
                });
                for (Player.LevelHistoryEntry entry : list) {
                    List<String> rec = new ArrayList<>();
                    rec.add(formatter.format(entry.date));
                    rec.add(p.first_name);
                    rec.add(p.last_name);
                    rec.add(p.type.toString());
                    rec.add("" + p.guest);
                    rec.add(entry.level.toJson());
                    printer.printRecord(rec);
                }
            }
        }
        printer.close();
    }
}

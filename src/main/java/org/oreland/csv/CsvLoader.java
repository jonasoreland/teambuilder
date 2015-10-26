package org.oreland.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.oreland.db.Repository;
import org.oreland.entity.Activity;
import org.oreland.entity.Level;
import org.oreland.entity.Player;
import org.oreland.entity.TargetLevel;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jonas on 10/5/15.
 */
public class CsvLoader {

    String dir = "csv";

    public String getActivitiesFilename() {
        return dir + "/" + "activities.csv";
    }

    public String getParticipantsFilename() {
        return dir + "/" + "participants.csv";
    }

    public String getInvitationsFilename() {
        return dir + "/" + "invitations.csv";
    }

    public String getPlayersFilename() {
        return dir + "/" + "players.csv";
    }

    public String getLevelsFilename() {
        return dir + "/" + "levels.csv";
    }

    public void load(Repository repo) throws IOException, ParseException {
        loadPlayers(repo);
        loadActivities(repo);
        loadInvitations(repo);
        loadParticipants(repo);
    }

    public void save(Repository repo) throws IOException, ParseException {
        new File(dir).mkdirs();
        savePlayers(repo);
        saveActivities(repo);
        saveInvitations(repo);
        saveParticipants(repo);
    }

    public void loadActivities(Repository repo) throws ParseException, IOException {
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

    public void saveActivities(Repository repo) throws IOException {
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

    public void loadInvitations(Repository repo) throws ParseException, IOException {
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

    public void saveInvitations(Repository repo) throws ParseException, IOException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd HH:ss");
        final Appendable out = new FileWriter(dir + "/" + "invitations.csv");
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader("game", "first_name", "last_name", "type", "invitation_date", "response", "response_date", "response_comment").print(out);
        for (Repository.Pair<Activity, Activity.Invitation> invitation : repo.getInvitations()) {
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

    public void loadParticipants(Repository repo) throws ParseException, IOException {
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
            repo.addParticipant(game, p);
        }
    }

    public void saveParticipants(Repository repo) throws ParseException, IOException {
        final Appendable out = new FileWriter(getParticipantsFilename());
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader("game", "first_name", "last_name", "type").print(out);
        for (Repository.Pair<Activity, Activity.Participant> participant : repo.getParticipants()) {
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
            TargetLevel level = TargetLevel.parseJson(repo, record.get("target"));
            repo.addTarget(p, level, d);
        }
    }

    private void savePlayers(Repository repo) throws IOException {
        final Appendable out = new FileWriter(getPlayersFilename());
        final CSVPrinter printer = CSVFormat.EXCEL.withHeader("date", "first_name", "last_name", "type", "guest", "target").print(out);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        Iterator<Player> players = repo.getPlayers();
        while (players.hasNext()) {
            Player p = players.next();
            if (p.type != Player.Type.PLAYER)
                continue;

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

package org.oreland.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.oreland.db.Repository;
import org.oreland.entity.Activity;
import org.oreland.entity.Level;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jonas on 10/5/15.
 */
public class CsvLoader {

    String dir = "csv";

    public void loadActivities(Repository repo) throws ParseException, IOException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(new FileReader(dir + "/" + "activities.csv"));
        for (CSVRecord record : records) {
            Activity g = new Activity();
            g.id = record.get("id");
            g.date = formatter.parse(record.get("date"));
            g.description = record.get("description");
            g.type = Activity.Type.parse(record.get("type"));
            g.level = Level.parse(record.get("level"));
            g.synced = Boolean.parseBoolean(record.get("synced"));
            repo.add(g);
        }
    }
    public void saveActivities(Repository repo) throws IOException {
        final Appendable out = new FileWriter(dir + "/" + "activities.csv");
        final CSVPrinter printer = CSVFormat.DEFAULT.withHeader("id", "date", "description", "level", "synced").print(out);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        for (Activity game : repo.getActivities()) {
            List rec = new ArrayList();
            rec.add(game.id);
            rec.add(formatter.format(game.date));
            rec.add(game.description);
            rec.add(game.type.toString());
            if (game.level != null)
                rec.add(game.level.toString());
            else
                rec.add("");
            rec.add(game.synced);
            printer.printRecord(rec);
        }
        printer.close();
    }

//    public void loadInvitations(Repository repo);
//    public void loadParticipants(Repository repo);
}

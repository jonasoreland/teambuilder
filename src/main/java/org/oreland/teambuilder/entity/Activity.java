package org.oreland.teambuilder.entity;

import java.lang.annotation.Target;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by jonas on 10/4/15.
 */
public class Activity {

    public Activity copy() {
        Activity a = new Activity();
        a.id = id;
        a.type = type;
        a.date = date;
        a.title = title;
        a.description = description;
        a.synced = synced;
        a.level = level;
        a.time = time;
        return a;
    }

    public TargetLevel getDistribution() {
        TargetLevel l = new TargetLevel();
        for (Participant p : participants) {
            l.getOrCreate(p.player.target_level.getBestMatchLevel()).count++;
        }

        return l;
    }

    public enum Type {
        GAME,
        TRAINING,
        CUP,
        REQUEST;

        public static Type parse(String type) {
            if (type.matches("GAME"))
                return GAME;
            else if (type.matches("TRAINING"))
                return TRAINING;
            else if (type.matches("CUP"))
                return CUP;
            else if (type.matches("REQUEST"))
                return REQUEST;
            System.out.println("type: " + type + " => NULL");
            return null;
        }

        public String toString() {
            return name();
        }
    }

    public enum Response {
        YES,
        NO,
        MAYBE,
        NO_RESPONSE;

        public static Response parse(String response) {
            if (response == null)
                return null;
            if (response.isEmpty())
                return NO_RESPONSE;
            String tmp = response.toLowerCase();
            if (tmp.matches("yes"))
                return YES;
            if (tmp.matches("no"))
                return NO;
            if (tmp.matches("maybe"))
                return MAYBE;
            return NO_RESPONSE;
        }
    }

    public static class Invitation {
        public Player player;
        public Response response;
        public String response_comment;
        public Date invitation_date;
        public Date response_date;
    }

    public static class Participant {
        public Player player;

        public Participant() {
        }

        public Participant(Player p) {
            this.player = p;
        }

        // TODO(jonas) : Grade each played game for each player ??
        // Level grade;
    }

    public String id;
    public Type type;
    public Date date;
    public String title;
    public String description;
    public String time;
    public boolean synced;
    public Level level;
    public List<Invitation> invitations = new ArrayList<Invitation>();
    public List<Participant> participants = new ArrayList<Participant>();

    public Activity() {
    }

    public Activity(String id, Date date, String desc, Type type) {
        this.id = id;
        this.title = desc;
        this.date = date;
        this.synced = false;
        this.type = type;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(type);
        sb.append(" ");
        sb.append(new SimpleDateFormat("yyyy-MM-dd").format(date));
        return sb.toString();
    }

    public boolean mergeable(Activity a2) {
        if (type != Type.TRAINING)
            return false;
        if (a2.type != Type.TRAINING)
            return false;
        if (date.compareTo(a2.date) != 0)
            return false;
        if (time != null && a2.time != null) {
            if (!time.contentEquals(a2.time))
                return false;
        }
        return true;
    }

    public void add(Participant part) {
        if (hasParticipant(part.player))
            return;
        participants.add(part);
    }

    public void add(Invitation invitation) {
        for (Invitation i : invitations) {
            if (i.player == invitation.player)
                return;
        }
        invitations.add(invitation);
    }

    public boolean hasParticipant(Player player) {
        for (Participant p : participants) {
            if (p.player == player)
                return true;
        }
        return false;
    }
}

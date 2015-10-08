package org.oreland.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by jonas on 10/4/15.
 */
public class Activity {

    public enum Type {
        GAME,
        TRAINING;

        public static Type parse(String type) {
            if (type.matches("GAME"))
                return GAME;
            else if (type.matches("TRAINING"))
                return TRAINING;
            System.out.println("type: " + type + " => NULL");
            return null;
        }

        public String toString() {
            return name();
        }
    };

    public enum Response {
        YES,
        NO,
        MAYBE,
        NO_RESPONSE;

        public static Response parse(String response) {
            if (response.isEmpty())
                return NO_RESPONSE;
            if (response.matches("YES"))
                return YES;
            if (response.matches("NO"))
                return NO;
            if (response.matches("MAYBE"))
                return MAYBE;
            return null;
        }
    }

    public static class Invitation {
        public Player player;
        public Response response;
        public Date invitation_date;
        public Date response_date;
    }

    public static class Participant {
        public Player player;
        // TODO(jonas) : Grade each played game for each player ??
        // Level grade;
    }

    public String id;
    public Type type;
    public Date date;
    public String title;
    public String description;
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
}

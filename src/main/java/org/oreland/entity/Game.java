package org.oreland.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class Game extends Activity {

    public Game(String id, Date date, String desc) {
        super(id, date, desc);
    }

    public enum Response {
        YES,
        NO,
        MAYBE,
        NO_RESPONSE
    }

    public class Invitation {
        public Player player;
        public Response response;
        public Date invitation_date;
        public Date response_date;
    }

    public class Participant {
        Player player;
        // TODO(jonas) : Grade each played game for each player ??
        // Level grade;
    }

    public Level level;
    public List<Invitation> invitations = new ArrayList<Invitation>();
    public List<Participant> participants = new ArrayList<Participant>();
}

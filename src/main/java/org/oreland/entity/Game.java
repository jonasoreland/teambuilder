package org.oreland.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class Game {

    public enum Response {
        YES,
        NO,
        MAYBE,
        NO_RESPONSE
    }

    public class Invitation {
        Player player;
        Response response;
        Date invitation_date;
        Date response_date;
    }

    public class Participant {
        Player player;
        Level grade;
    }

    long id;
    Level level;
    List<Invitation> invitations = new ArrayList<Invitation>();
    List<Participant> participants = new ArrayList<Participant>();
}

package org.oreland.entity;

import java.util.Date;

/**
 * Created by jonas on 10/4/15.
 */
public class Activity {

    public String id;
    public Date date;
    public String description;

    public Activity(String id, Date date, String desc) {
        this.id = id;
        this.description = desc;
        this.date = date;
    }
}

package org.oreland.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jonas on 10/1/15.
 */
public class TargetLevel {

    public class Distribution {
        public Level level;
        public int count;
    }

    public List<Distribution> distribution = new ArrayList<Distribution>();
}

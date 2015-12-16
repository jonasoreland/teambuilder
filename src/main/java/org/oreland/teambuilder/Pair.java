package org.oreland.teambuilder;

/**
 * Created by jonas on 12/6/15.
 */
public class Pair<T, U> {
    public Pair(T t, U u) {
        this.first = t;
        this.second = u;
    }

    public T first;
    public U second;
}

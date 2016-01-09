package org.oreland.teambuilder.db;

/**
 * Created by jonas on 1/9/16.
 */
public abstract class Filter<T> {
    public abstract boolean OK(T t);
}

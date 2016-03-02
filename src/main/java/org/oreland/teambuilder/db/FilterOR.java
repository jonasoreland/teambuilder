package org.oreland.teambuilder.db;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jonas on 3/2/16.
 */
public class FilterOR<T> extends Filter<T> {

    List<Filter<T>> group = new ArrayList<>();

    public FilterOR(Filter<T> f1, Filter<T> f2) {
        group.add(f1);
        group.add(f2);
    }

    public FilterOR(Filter<T> f1[]) {
        for (Filter<T> f: f1){
            group.add(f);
        }
    }

    @Override
    public boolean OK(T t) {
        for (Filter<T> f : group) {
            if (f.OK(t))
                return true;
        }
        return false;
    }
}

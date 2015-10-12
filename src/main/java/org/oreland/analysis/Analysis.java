package org.oreland.analysis;

import org.oreland.db.Repository;
import org.oreland.entity.Player;

import java.util.Iterator;

/**
 * Created by jonas on 10/10/15.
 */
public class Analysis {

    Repository repo;

    public Analysis(Repository repo) {
        this.repo = repo;
    }

    private abstract class Filter<T> {
        public abstract boolean OK(T t);
    };

    private int count(Iterator iterator) {
        int count = 0;
        while (iterator.next() != null)
            count++;
        return count;
    }

    public int countUngradedPlayers() {
        return count(getUngradedPlayers());
    }

    public Iterator<Player> getUngradedPlayers() {
        return new FilteredIterator<>(repo.getPlayers(), new Filter<Player>() {
            @Override
            public boolean OK(Player player) {
                return player.type == Player.Type.PLAYER && player.level_history.isEmpty();
            }
        });
    }

    private class FilteredIterator<T> implements Iterator<T> {

        private final Iterator<T> all;
        private final Filter<T> filter;
        private T next = null;

        FilteredIterator(Iterator<T> all, Filter<T> filter) {
            this.all = all;
            this.filter = filter;
        }
        @Override
        public boolean hasNext() {
            next = getNext();
            if (next != null)
                return true;
            return false;
        }

        @Override
        public T next() {
            if (next != null) {
                T p = next;
                next = null;
                return p;
            }
            return getNext();
        }
        private T getNext() {
            while (true) {
                if (!all.hasNext())
                    return null;
                T p = all.next();
                if (p == null)
                    return null;
                if (filter.OK(p))
                    return p;
            }
        }

        @Override
        public void remove() {

        }
    };
}

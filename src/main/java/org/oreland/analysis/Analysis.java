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
        return getPlayers(new Filter<Player>() {
            @Override
            public boolean OK(Player player) {
                return player.level_history.isEmpty();
            }
        });
    }

    Iterator<Player> getPlayers(final Filter<Player> filter) {
        final Iterator<Player> all = repo.getPlayers();
        return new Iterator<Player>() {
            Player next = null;
            @Override
            public boolean hasNext() {
                next = getNext();
                if (next != null)
                    return true;
                return false;
            }

            private Player getNext() {
                while (true) {
                    if (!all.hasNext())
                        return null;
                    Player p = all.next();
                    if (p == null)
                        return null;
                    if (filter.OK(p))
                        return p;
                }
            }

            @Override
            public Player next() {
                if (next != null) {
                    Player p = next;
                    next = null;
                    return p;
                }
                return getNext();
            }

            @Override
            public void remove() {
            }
        };
    }
}

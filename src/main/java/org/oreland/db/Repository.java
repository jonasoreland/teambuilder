package org.oreland.db;

import org.oreland.entity.Activity;
import org.oreland.entity.Player;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by jonas on 10/4/15.
 */
public class Repository {
    HashMap<String, Activity> activities = new HashMap<>();
    HashMap<String, Player> players = new HashMap<>();

    public static class Pair<T,U> {
        public Pair(T t, U u) {
            this.first = t;
            this.second = u;
        }
        public T first;
        public U second;
    };

    public Player add(Player p) {
        if (!players.containsKey(p.ssno)) {
            players.put(p.ssno, p);
        }
        return players.get(p.ssno);
    }

    public Activity add(Activity game) {
        if (!activities.containsKey(game.id)) {
            activities.put(game.id, game);
        }
        return activities.get(game.id);
    }

    public Iterable<Activity> getActivities() {
        return activities.values();
    }

    public Iterable<Pair<Activity, Activity.Invitation>> getInvitations() {
        return new Iterable<Pair<Activity, Activity.Invitation>>() {
            Iterator<Activity> activities = getActivities().iterator();
            Iterator<Activity.Invitation> invitations = null;

            @Override
            public Iterator<Pair<Activity, Activity.Invitation>> iterator() {
                return new Iterator<Pair<Activity, Activity.Invitation>>() {
                    Activity activity = null;
                    Pair<Activity, Activity.Invitation> next = null;

                    @Override
                    public boolean hasNext() {
                        next = getNext();
                        return next != null;
                    }

                    @Override
                    public Pair<Activity, Activity.Invitation> next() {
                        Pair<Activity, Activity.Invitation> ret = next;
                        next = null;
                        return ret;
                    }

                    @Override
                    public void remove() {
                    }

                    private Pair<Activity, Activity.Invitation> getNext() {
                        Activity.Invitation invitation = null;
                        while (invitation == null) {
                            while (invitations == null) {
                                if (activities.hasNext())
                                    activity = activities.next();
                                else
                                    return null;
                                invitations = activity.invitations.iterator();
                            }
                            if (invitations.hasNext())
                                invitation = invitations.next();
                            else
                                invitations = null;
                        }
                        return new Pair<Activity, Activity.Invitation>(activity, invitation);
                    }
                };
            }
        };
    }

    public Iterable<Pair<Activity, Activity.Participant>> getParticipants() {
        return new Iterable<Pair<Activity, Activity.Participant>>() {
            Iterator<Activity> activities = getActivities().iterator();
            Iterator<Activity.Participant> participants = null;

            @Override
            public Iterator<Pair<Activity, Activity.Participant>> iterator() {
                return new Iterator<Pair<Activity, Activity.Participant>>() {
                    Activity activity = null;
                    Pair<Activity, Activity.Participant> next = null;

                    @Override
                    public boolean hasNext() {
                        next = getNext();
                        return next != null;
                    }

                    @Override
                    public Pair<Activity, Activity.Participant> next() {
                        Pair<Activity, Activity.Participant> ret = next;
                        next = null;
                        return ret;
                    }

                    @Override
                    public void remove() {
                    }

                    private Pair<Activity, Activity.Participant> getNext() {
                        Activity.Participant participant = null;
                        while (participant == null) {
                            while (participants == null) {
                                if (activities.hasNext())
                                    activity = activities.next();
                                else
                                    return null;
                                participants = activity.participants.iterator();
                            }
                            if (participants.hasNext())
                                participant = participants.next();
                            else
                                participants = null;
                        }
                        return new Pair<Activity, Activity.Participant>(activity, participant);
                    }
                };
            }
        };
    }

    public Activity getActivity(String game_id) {
        return activities.get(game_id);
    }
};

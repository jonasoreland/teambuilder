package org.oreland.db;

import org.oreland.entity.Activity;
import org.oreland.entity.Level;
import org.oreland.entity.Player;
import org.oreland.entity.TargetLevel;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jonas on 10/4/15.
 */
public class Repository {
    HashMap<String, Activity> activities = new HashMap<>();
    HashMap<String, Player> playersByName = new HashMap<>();
    List<Level> levels = new ArrayList<>();

    public Iterable<Level> getLevels() {
        return levels;
    }

    public void addLevel(Level l) {
        if (Level.parse(this, l.name) == null)
            levels.add(l);
    }

    public Iterator<Player> getPlayers() {
        return playersByName.values().iterator();
    }

    public static class Pair<T, U> {
        public Pair(T t, U u) {
            this.first = t;
            this.second = u;
        }

        public T first;
        public U second;
    }

    ;

    public Player add(Player p) {
        String key = p.first_name + p.last_name;
        if (!playersByName.containsKey(key))
            playersByName.put(key, p);
        return playersByName.get(key);
    }

    public Player getPlayer(String first_name, String last_name) {
        String key = first_name + last_name;
        return playersByName.get(key);
    }

    public void addTarget(Player p, TargetLevel level, Date date) {
        if (level == null)
            return;
        if (p.target_level == null || !p.target_level.equal(level)) {
            Player.LevelHistoryEntry entry = new Player.LevelHistoryEntry();
            entry.level = level;
            entry.date = date;
            p.target_level = level;
            p.level_history.add(entry);
        }
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

    public void addParticipant(Activity activity, Player p) {
        Activity.Participant part = new Activity.Participant();
        part.player = add(p);
        if (!activity.participants.contains(part)) {
            activity.participants.add(part);
            part.player.games_played.add(activity);
        }
    }

    public void addInvitation(Activity activity, Activity.Invitation invitation) {
        if (!activity.invitations.contains(invitation)) {
            activity.invitations.add(invitation);
            invitation.player.games_invited.add(invitation);
        }
    }
};

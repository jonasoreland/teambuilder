package org.oreland.teambuilder.db;

import org.oreland.teambuilder.Pair;
import org.oreland.teambuilder.entity.Activity;
import org.oreland.teambuilder.entity.Level;
import org.oreland.teambuilder.entity.Player;
import org.oreland.teambuilder.entity.TargetLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jonas on 10/4/15.
 */
public class Repository {
    private HashMap<String, Activity> activities = new HashMap<>();
    private HashMap<String, Player> playersByName = new HashMap<>();
    private List<Level> levels = new ArrayList<>();

    public void reset() {
        activities.clear();
        playersByName.clear();
        levels.clear();
    }

    // "Deep copy"
    public Repository clone() {
        Repository rep = new Repository();
        for (Player p : playersByName.values()) {
            rep.add(p.copy());  // shallow copy
        }
        for (Activity a : activities.values()) {
            Activity new_a = a.copy();  // shallow copy
            rep.add(new_a);
            for (Activity.Invitation inv : a.invitations) {
                Activity.Invitation new_inv = new Activity.Invitation();
                new_inv.invitation_date = inv.invitation_date;
                new_inv.player = rep.getPlayer(inv.player.first_name, inv.player.last_name);
                new_inv.response = inv.response;
                new_inv.response_comment = inv.response_comment;
                new_inv.response_date = inv.response_date;
                rep.addInvitation(new_a, new_inv);
            }
            for (Activity.Participant par : a.participants) {
                Activity.Participant new_par = new Activity.Participant();
                Player p = rep.getPlayer(par.player.first_name, par.player.last_name);
                if (p == null) {
                    System.out.println("no player " + par.player.first_name + ", " + par.player.last_name);
                }
                rep.addParticipant(new_a, p);
            }
        }
        return rep;
    }

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

    public void remove(Activity dup) {
        activities.remove(dup.id);
        for (Activity.Invitation inv : dup.invitations) {
            inv.player.games_invited.remove(inv);
        }
        for (Activity.Participant part : dup.participants) {
            part.player.games_played.remove(part);
        }
        dup.invitations.clear();
        dup.participants.clear();
    }

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
        activity.add(new Activity.Participant(p));
        p.add(activity);
    }

    public void addInvitation(Activity activity, Activity.Invitation invitation) {
        activity.add(invitation);
        invitation.player.add(invitation);
    }

    public List<Activity> selectActivities(Date startDate, Date endDate, Activity.Type type) {
        List<Activity> res = new ArrayList<Activity>();
        for (Activity act : getActivities()) {
            if (act.type != type)
                continue;
            if (act.date.before(startDate))
                continue;
            if (act.date.after(endDate))
                continue;
            res.add(act);
        }
        return res;
    }

    //    Date first, Date second
    public void prune(Filter<Activity> filter) {
        List<Activity> remove = new ArrayList<>();
        // remove all activities before first and after (includes) second
        for (Activity a : getActivities()) {
            if (!filter.OK(a))
                remove.add(a);
        }

        for (Activity a : remove) {
            remove(a);
        }
        xref();
    }

    // recompute cross references
    public void xref() {
        for (Player p : playersByName.values()) {
            p.games_invited.clear();
            p.games_played.clear();
        }

        for (Activity a : activities.values()) {
            for (Activity.Invitation i : a.invitations) {
                Player p = add(i.player);
                p.add(i);
            }
            for (Activity.Participant i : a.participants) {
                Player p = add(i.player);
                if (p == null) {
                    System.out.println("p == null: " + i.player);
                }
                p.add(a);
            }
        }
    }

    public void dump() {
        List<Activity> new_list = new ArrayList<>();
        {
            Iterator<Activity> a = activities.values().iterator();
            while (a.hasNext()) {
                new_list.add(a.next());
            }
        }
        Collections.sort(new_list, new Comparator<Activity>() {
            @Override
            public int compare(Activity a1, Activity a2) {
                return a1.date.compareTo(a2.date);
            }
        });
        for (Activity a : new_list) {
            System.out.println("* " + a);
            for (Activity.Participant p : a.participants) {
                System.out.println("\t" + p.player);
            }
        }
        for (Player p : playersByName.values()) {
            System.out.println("* " + p);
            for (Activity a : p.games_played) {
                System.out.println("\tplayed:" + a);
            }
            for (Activity.Invitation i : p.games_invited) {
                System.out.println("\tinvited: " + i);
            }
        }
    }
}

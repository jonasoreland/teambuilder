package org.oreland.teambuilder.sync;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.oreland.teambuilder.Context;
import org.oreland.teambuilder.Pair;
import org.oreland.teambuilder.db.Repository;
import org.oreland.teambuilder.entity.Activity;
import org.oreland.teambuilder.entity.Fee;
import org.oreland.teambuilder.entity.Level;
import org.oreland.teambuilder.entity.Payment;
import org.oreland.teambuilder.entity.Player;
import org.oreland.teambuilder.entity.TargetLevel;
import org.oreland.teambuilder.sync.util.FormValues;
import org.oreland.teambuilder.sync.util.SyncHelper;
import org.oreland.teambuilder.ui.Dialog;
import org.oreland.teambuilder.ui.DialogBuilder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Created by jonas on 10/1/15.
 */
public class MyClub extends DefaultSynchronizer {

    private static final String CHARSET = "utf-8";
    private static final String START_URL = "https://www.myclub.se/";
    private static final String BASE_URL = "https://member.myclub.se";
    private static final String LOGIN_URL = "https://accounts.myclub.se/d/users/sign_in"; //.json";
    private static final String CALENDAR_URL = BASE_URL + "/activities/team/show/";
    private static final String PARTICIPANTS_URL = BASE_URL + "/activities/team/view_parts/";
    private static final String INVITATIONS_URL = BASE_URL + "/activities/team/view_invited/";
    private static final String DESCRIPTION_URL = BASE_URL + "/activities/team/edit_info/";
    private static final String PLAYER_URL = BASE_URL + "/teams/";
    private static final String PLAYER_JSON_URL = BASE_URL + "/teams/";

    private static final String CLUB_NAME = "clubName";
    private static final String CLUB_KEY = "club";

    private static final String SECTION_NAME = "sectionName";
    private static final String SECTION_KEY = "section";

    private static final String TEAM_NAME = "teamName";
    private static final String TEAM_KEY = "team";
    private static final String TEAM_NO = "teamno";

    private static final String PERIOD_NAME = "periodName";
    private static final String PERIOD_KEY = "period";

    private static final String SPELAR_INFO = "Spelarinfo";

    long id = 0;
    private String username = null;
    private String password = null;
    private String authToken = null;

    @Override
    public String getName() {
        return "MyClub";
    }

    @Override
    public void init(Properties config) {
        username = config.getProperty("username", null);
        password = config.getProperty("password", null);
    }

    @Override
    public void reset() {
        username = null;
        password = null;
        authToken = null;
    }

    private void addRequestHeaders(HttpURLConnection conn) {
        conn.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.addRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.0; Trident/5.0)");
        conn.addRequestProperty("Accept-Encoding", CHARSET);
        conn.addRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.addRequestProperty("Connection", "keep-alive");
        conn.addRequestProperty("Accept-Charset", CHARSET);
    }

    public Status login(Properties config, DialogBuilder builder) {
        System.out.println("Login...");
        for (int i = 0; i < 3; i++) {
            Status s = connect();
            if (s == Status.NEED_AUTH) {
                builder.setQuestion("Username: ");
                Dialog.Result result = builder.build().show();
                if (result != Dialog.Result.OK)
                    return s;
                username = result.stringResult;
                builder.setQuestion("Password: ");
                result = builder.build().show();
                if (result != Dialog.Result.OK)
                    return s;
                password = result.stringResult;
            } else if (s == Status.OK) {
                config.put("username", username);
                config.put("password", password);
                break;
            } else {
                return s;
            }
        }
        return Status.ERROR;
    }

    public Status setup(Context ctx, Properties config, DialogBuilder builder) {
        login(config, builder);
        Status s;
        if ((s = setupClub(ctx, config, builder)) != Status.OK)
            return s;

        if ((s = setupSection(ctx, config, builder)) != Status.OK)
            return s;

        if ((s = setupTeam(ctx, config, builder)) != Status.OK)
            return s;

        if ((s = setupPeriod(ctx, config, builder)) != Status.OK)
            return s;

        return Status.OK;
    }

    public Status setupClub(Context ctx, Properties config, DialogBuilder builder) {
        Document doc;
        try {
            if (!config.containsKey(CLUB_KEY)) {
                Specifier club = select("Select club: ", builder, BASE_URL, "a[href*=change_club]");
                config.setProperty(CLUB_NAME, club.name);
                config.setProperty(CLUB_KEY, club.key);
            }
            setClubEx(new Specifier(config.getProperty(CLUB_NAME), config.getProperty(CLUB_KEY)));
        } catch (IOException e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }

    private Status setupSection(Context ctx, Properties config, DialogBuilder builder) {
        Document doc;
        try {
            Specifier section = getCurrentSection(ctx);
            if (section == null) {
                section = select("Select section: ", builder, BASE_URL, "a[href*=change_section]");
            }
            setSection(ctx, section);
        } catch (IOException e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }

    private Status setupTeam(Context ctx, Properties config, DialogBuilder builder) {
        Document doc;
        try {
            Specifier team = getCurrentTeam(ctx);
            if (team == null) {
                team = select("Select team: ", builder, BASE_URL, "a[href*=change_team]");
            }
            setTeam(ctx, team);
        } catch (IOException e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }

    private Status setupPeriod(Context ctx, Properties config, DialogBuilder builder) {
        Document doc;
        try {
            Specifier period = getCurrentPeriod(ctx);
            if (period == null) {
                // matches HT-2xxx and VT-2xxx
                period = select("Select period: ", builder, CALENDAR_URL, "a[href*=T-2]");
            }
            setPeriod(ctx, period);
        } catch (IOException e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }

    private Status setupSpelarinfo(Context ctx, Properties config, DialogBuilder builder) {
        Document doc;
        try {
            if (!config.containsKey(SPELAR_INFO)) {
                doc = get(PLAYER_URL + config.getProperty(TEAM_NO) + "/members/");
                Element element = doc.select("p:contains(Spelarinformation) > *").first();
                String spelarinfo_field = element.attr("value");
                config.put(SPELAR_INFO, spelarinfo_field);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }

    private void setClubEx(Specifier club) throws IOException {
        System.out.println("Loading club: " + club.name);
        Document doc = get(BASE_URL + club.key);
    }

    private void setSectionEx(Specifier section) throws IOException {
        System.out.println("Loading section: " + section.name);
        Document doc = get(BASE_URL + section.key);
    }

    private void setTeamEx(Specifier team) throws IOException {
        System.out.println("Loading team: " + team.name);
        Document doc = get(BASE_URL + team.key);
    }

    private void setPeriodEx(Specifier period) throws IOException {
        System.out.println("Loading period: " + period.name);
        Document doc = get(BASE_URL + period.key);
    }

    private List<Specifier> list(String baseUrl, String query) throws IOException {
        Document doc = get(baseUrl);
        Set<String> values = new HashSet<>();
        List<Specifier> list = new ArrayList<>();
        for (Element e : doc.select(query)) {
            String link = e.attr("href");
            String value = e.text();
            if (!values.contains(value)) {
                values.add(value);
                list.add(new Specifier(value, link));
            }
        }
        return list;
    }

    private Specifier select(String prompt, DialogBuilder builder, String baseUrl, String query) throws IOException {
        Document doc = get(baseUrl);
        Set<String> values = new HashSet<>();
        List<String> choices = new ArrayList<>();
        List<String> choiceValues = new ArrayList<>();
        for (Element e : doc.select(query)) {
            String link = e.attr("href");
            String value = e.text();
            if (!values.contains(value)) {
                values.add(value);
                choices.add(value);
                choiceValues.add(link);
            }
        }

        builder.setQuestion(prompt);
        builder.setChoices(choices);
        Dialog.Result result = builder.build().show();
        return new Specifier(choices.get(result.intResult - 1),
                choiceValues.get(result.intResult - 1));
    }

    public List<Specifier> selectMulti(String prompt, DialogBuilder builder, String baseUrl, String query) throws IOException {
        Document doc = get(baseUrl);
        Set<String> values = new HashSet<>();
        List<String> choices = new ArrayList<>();
        List<String> choiceValues = new ArrayList<>();
        for (Element e : doc.select(query)) {
            String link = e.attr("href");
            String value = e.text();
            if (!values.contains(value)) {
                values.add(value);
                choices.add(value);
                choiceValues.add(link);
            }
        }

        builder.setQuestion(prompt);
        builder.setChoices(choices);
        Dialog.Result result = builder.build().show();
        List<Specifier> returnValue = new ArrayList<>();
        for (int res : result.intResults) {
            returnValue.add(new Specifier(choices.get(res - 1), choiceValues.get(res - 1)));
        }
        return returnValue;
    }

    private Document get(String baseUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl).openConnection();
        // conn.setInstanceFollowRedirects(false);
        addRequestHeaders(conn);
        conn = send(baseUrl, conn);
        Document doc = Jsoup.parse(conn.getInputStream(), "UTF-8", baseUrl);
        conn.disconnect();
        return doc;
    }

    private JSONArray getJsonArray(String baseUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl).openConnection();
        // conn.setInstanceFollowRedirects(false);
        addRequestHeaders(conn);
        conn = send(baseUrl, conn);
        JSONArray doc = SyncHelper.parseJsonArray(conn.getInputStream());
        conn.disconnect();
        return doc;
    }

    @Override
    public Status connect() {
        Exception ex = null;
        HttpURLConnection conn = null;
        formValues.clear();

        Status s = Status.NEED_AUTH;
        s.authMethod = AuthMethod.USER_PASS;
        if (username == null || password == null) {
            return s;
        }

        cookies.clear();

        try {
            /**
             * connect to START_URL to get cookies/formValues
             */
            conn = (HttpURLConnection) new URL(START_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            addRequestHeaders(conn);
            {
                conn = send(START_URL, conn);
            }
            conn.disconnect();

            /**
             * connect to BASE_URL to get cookies/formValues
             */
            conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            addRequestHeaders(conn);
            {
                conn = send(BASE_URL, conn);
                getFormValues(conn);
                authToken = formValues.get("authenticity_token");
            }
            conn.disconnect();

            if (authToken == null) {
                return Status.ERROR;
            }

            /**
             * Then login using a post
             */
            FormValues kv = new FormValues();
            kv.put("user[email]", username);
            kv.put("user[password]", password);
            kv.put("authenticity_token", authToken);
            kv.put("utf8", "&#x2713;");
            kv.put("user[remember_me]", "1");
            kv.put("commit", "Logga in");

            conn = (HttpURLConnection) new URL(LOGIN_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            conn.setRequestMethod(RequestMethod.POST.name());
            addRequestHeaders(conn);
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            conn.setRequestProperty("Host", "accounts.myclub.se");
            conn.setRequestProperty("Referer", "https://member.myclub.se");

            {
                OutputStream wr = new BufferedOutputStream(
                        conn.getOutputStream());
                kv.write(wr);
                wr.flush();
                wr.close();
                send(LOGIN_URL, conn);
            }

            conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            addRequestHeaders(conn);
            {
                conn = send(START_URL, conn);
                String html = SyncHelper.readInputStream(conn.getInputStream());
                conn.disconnect();
                Document doc = Jsoup.parse(html);
                if (doc.select("a[href*=/user/logout]").size() == 0) {
                    return Status.NEED_AUTH;
                }
            }

            return Status.OK;
        } catch (MalformedURLException e) {
            ex = e;
        } catch (Exception e) {
            ex = e;
        }

        if (conn != null)
            conn.disconnect();

        s.ex = ex;
        if (ex != null) {
            ex.printStackTrace();
        }
        return s;
    }

    private HttpURLConnection send(String startUrl, HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                String newUrl = conn.getHeaderField("Location");
                System.err.println("status: " + status +
                    ", Location: " + newUrl);
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                addRequestHeaders(conn);
                return send(newUrl, conn);
            }
        }
        return conn;
    }

    public void loadPlayers(Context ctx) throws IOException {
        System.out.println("Load player list");
        ctx.prop.remove(SPELAR_INFO); // always reload spelar info
        String key = ctx.prop.getProperty(SPELAR_INFO);
        if (key == null) {
            setupSpelarinfo(ctx, ctx.prop, ctx.builder);
            key = ctx.prop.getProperty(SPELAR_INFO);
        }
        JSONArray players = getJsonArray(getPlayerJsonUrl(ctx));
        for (int i = 0; i < players.length(); i++) {
            JSONObject o = players.getJSONObject(i);
            Player p = new Player(o.getString("first_name"), o.getString("last_name"));
            p.type = o.getString("is_leader").equalsIgnoreCase("Ledare") ? Player.Type.LEADER : Player.Type.PLAYER;
            p = ctx.repo.add(p);
            String targetstr = o.optString(key);
            TargetLevel target = TargetLevel.parseJson(ctx.repo, targetstr);
            if (target != null) {
                ctx.repo.addTarget(p, target, Calendar.getInstance().getTime());
            }
        }
    }

    public void loadActivities(Context ctx) throws IOException, ParseException {
        System.out.println("Load activities list");
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -1);
        Date today = (Date) c.getTime().clone();
        c.add(Calendar.DAY_OF_YEAR, 8);
        Date limit = c.getTime();

        // alias
        Repository repo = ctx.repo;

        Document doc = get(BASE_URL + getCurrentPeriod(ctx).key);
        Element table = doc.select("table[id=grid_activities_table]").first();
//  0  <td>Tr?ning</td>
//  1  <td><span class="hidden">20150930</span>30/09</td>
//  2  <td class="visible-lg">Ons</td>
//  3  <td>17:00&nbsp;-&nbsp;18:00</td>
//  4  <td class="visible-lg visible-md">Beckombergahallen kl. 16:45</td>
//  5  <td class="visible-lg">Tr?ning</td>
//  6  <td class="visible-lg" title="Annan">Annan</td>
//  7  <td class="visible-lg visible-md"><a class="tool-tip" title="Ledare/Deltagare" href="/activities/team/view_parts/746587/">2/15</a></td>
//  8  <td class="visible-lg visible-md"><a class="tool-tip" title="Kallad (Ja/Nej/Kanske)" href="/activities/team/view_invited/746587/">46 (11/16/1)</a></td>
//  9  <td class="visible-lg visible-md">Godk?nd</td>
//  10  <td class="button_cell"><a href="/activities/team/edit/746587/" class="btn btn-default btn-xs">V?lj</a></td>
//  11  <td class="button_cell"><a class="btn btn-xs btn-danger" href=#onclick="$.fn.do_confirm('/activities/team/delete/746587/');"><i class="fa fa-trash"></i></a></td>
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        for (Element row : table.select("tr")) {
            Elements columns = row.select("td");
            if (columns.size() == 0)
                continue;
            if (columns.size() < 6)
                continue;
            String type = columns.get(5).text();
            Activity activity = null;
            String desc = columns.get(0).text();
            String date = columns.get(1).text().substring(0, 8);
            String id = columns.get(7).select("a[href]").first().attr("href").replace("/activities/team/view_parts/", "").replace("/", "");
            if (type.equals("Match")) {
                activity = repo.add(new Activity(id, formatter.parse(date), desc, Activity.Type.GAME));
            } else if (type.matches("Tr.*ning")) {
                activity = repo.add(new Activity(id, formatter.parse(date), desc, Activity.Type.TRAINING));
            } else if (type.matches("Cup")) {
                activity = repo.add(new Activity(id, formatter.parse(date), desc, Activity.Type.CUP));
            } else if (type.matches("F.*rfr.*gan")) {
                activity = repo.add(new Activity(id, formatter.parse(date), desc, Activity.Type.REQUEST));
            }
            if (activity != null) {
                activity.time = columns.get(3).text().replace('\u00a0', ' ');
            }

            if (activity != null && (activity.type == Activity.Type.GAME || activity.type == Activity.Type.CUP) && activity.level == null) {
                System.err.println("Loading level for " + activity.toString());
                loadActivityLevel(repo, activity);
            }
            if (activity != null && activity.synced == false && activity.date.before(limit)) {
                System.err.println("Loading invitations/participants for " + activity.toString());
                loadInvitations(ctx, activity);
                loadParticipants(ctx, activity);
                if (activity.date.before(today)) {
                    String status = columns.get(9).text();
                    if (status.matches(".*[Gg]odk.*nd") || status.matches(".*Delvis godk.*")) {
                        System.out.println(activity + " is complete.");
                        activity.synced = true;
                    }
                }
            }
        }
    }

    private void loadActivityLevel(Repository repo, Activity activity) throws IOException {
        Document doc = get(getDescriptionUrl(activity));
        String description = doc.select("textarea[id=id_description]").first().text();
        Set<Level> match = new HashSet<>();
        for (Level l : repo.getLevels()) {
            if (l.Match(description)) {
                match.add(l);
            }
        }
        if (match.size() == 1) {
            activity.level = match.iterator().next();
        } else {
            // Could not match level, need to ask user!
            activity.description = description;
        }
    }

    private void loadParticipants(Context ctx, Activity activity) throws IOException {
        Repository repo = ctx.repo;
        activity.participants.clear();
        Document doc = get(getParticipantsUrl(ctx, activity));
        String tables[] = new String[]{"participants_table", "leaders_table"};
        Player.Type types[] = new Player.Type[]{Player.Type.PLAYER, Player.Type.LEADER};
        for (int i = 0; i < tables.length; i++) {
            String key = tables[i];
            Element table = doc.select("table[id=" + key + "]").first().select("tbody").first();
            for (Element row : table.select("tr")) {
                Elements columns = row.select("td");
                if (columns.size() < 6)
                    continue;

// <tr>
// <td>Albin</td>
// <td>Str?mgren</td>
// <td>20061102xxxx</td>
// <td>Godk?nd</td>
// <td></td>
// <td></td>
// </tr>
                Player p = new Player(columns.get(0).text(), columns.get(1).text());
                p.type = types[i];
                if (repo.getPlayer(p.first_name, p.last_name) == null) {
                    // Player that participate but not's in player list is guests
                    p.guest = true;
                }
                repo.addParticipant(activity, p);
            }
        }
    }

    private void loadInvitations(Context ctx, Activity activity) throws IOException, ParseException {
        Repository repo = ctx.repo;
        activity.invitations.clear();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:ss");
        Document doc = get(getInvitationsUrl(ctx, activity));
        Element table = doc.select("table").first().select("tbody").first();
        for (Element row : table.select("tr")) {
            Elements columns = row.select("td");
            if (columns.size() < 6)
                continue;
            // 0 <td><i class='fa fa-user-circle-o'></i> Malin</td>
            // 1 <td>Broman Lindfors</td>
            // 2 <td class="visible-lg visible-md"><p class="btn btn-xs tool-tip pop-over" href="#" data-toggle="popover" data-content="malinbroman@yahoo.se" title=""><i class="fa fa-envelope"></i></p></td>
            // 3 <td class="hidden-xs">1</td>
            // 4 <td class="visible-lg">2018-02-12 09:29</td>
            // 5 <td class="hidden-xs">-</td>
            // 6 <td><div class="btn-group" data-toggle="buttons-radio"><button type="button" data-id="6564120" data-response="yes" class="respond btn active btn-success btn-xs">Ja</button><button type="button" data-id="6564120" data-response="no" class="respond btn btn-default btn-xs">Nej</button></div><p class="btn btn-xs tool-tip pop-over" href="#" data-toggle="popover" data-content="Kan ta med två barn i bilen (utöver mina två egna)" title=""><i class="fa fa-comment"></i></p></td>
            // 7 <td class="visible-lg visible-md">2018-02-13 20:57</td>
            // 8 <td class="button_cell"><a class="btn btn-xs btn-default" data-toggle="modal" href="#send_email_6564120" "title="Skicka inbjudsningsmejl nu."><i class="fa fa-envelope-o"></i></a></td>

            //          <tr>
//       0     <td class=" sorting_1">Alfred</td>
//       1     <td class=" ">Wettergren</td>
//       2     <td class="hidden-xs ">Deltagare</td>
//       3     <td class="visible-lg visible-md "><p class="btn btn-xs tool-tip pop-over" title="" data-content="mail.address@somewhere.org" data-toggle="popover" href="#" data-original-title=""></p></td>
//       4     <td class="hidden-xs ">1</td>
//       5     <td class="visible-lg ">2015-10-08 12:25</td>
//       6     NEXT MAIL <td class="visible-lg "></td>
//       7     <td class=" ">
//            <div class="btn-group" data-toggle="buttons-radio">
//            <button class="respond btn active btn-success btn-xs" data-response="yes" data-id="1648554" type="button">Ja</button>
//            <button class="respond btn btn-default btn-xs" btn-default="" data-response="maybe" data-id="1648554" type="button">Kanske</button>
//            <button class="respond btn btn-default btn-xs" data-response="no" data-id="1648554" type="button">Nej</button>
//            </div>
//            </td>
//       8     <td class="visible-lg visible-md ">2015-10-08 14:38</td>
//       9     <td class="button_cell "></td>
//          </tr>

            int first_name_col = 0;
            int last_name_col = 1;
            int invitation_date_col = 4;
            int response_col = 6;
            int response_date_col = 7;

            if (columns.size() < 8) {
              // Completed activities has less columns...
              response_col = 5;
              response_date_col = 6;
            }

            // If no invitation is sent...skip player
            if (columns.get(invitation_date_col).text().isEmpty())
                continue;

            Player p = new Player(
                columns.get(first_name_col).text(),
                columns.get(last_name_col).text());
            if (repo.getPlayer(p.first_name, p.last_name) == null) {
                continue;
            }
            Activity.Invitation invitation = new Activity.Invitation(activity);
            invitation.player = repo.add(p);
            if (!columns.get(invitation_date_col).text().isEmpty())
              invitation.invitation_date = formatter.parse(columns.get(
                    invitation_date_col).text());
            if (!columns.get(response_date_col).text().isEmpty()) {
                invitation.response_date = formatter.parse(columns.get(response_date_col).text());
                Element col = columns.get(response_col);
                Element comment = col.select("p").first();
                if (comment != null) {
                    invitation.response_comment = comment.attr("data-content");
                }
                for (Element e : col.select("button")) {
                    if (e.attr("class").contains("active")) {
                        invitation.response = Activity.Response.parse(e.attr("data-response"));
                    }
                }
                if (invitation.response == null) {
                    System.err.println("Unable to find response in: " + col.toString());
		    invitation.response = Activity.Response.parse("maybe");
		}
            }
            repo.addInvitation(activity, invitation);
        }
    }

    private Payment parsePayment(Fee fee, Element col) {
        if (fee == null)
            return null;
        String txt = col.toString().toLowerCase();
        if (txt.contains("ej fakturerad") || txt.contains("not-invoiced"))
            return null;

        if (txt.contains("ej betald"))
            return new Payment(fee, 0);

        Pattern p = Pattern.compile("umma: [0-9]+");
        Matcher m = p.matcher(txt);
        if (m.find()) {
            String s = m.group();
            Integer i = Integer.valueOf(s.substring("umma: ".length()));
            return new Payment(fee, i.intValue());
        }
        System.out.println("parsePayment(" + fee + ", " + col.toString() + " => null");
        return null;
    }

    private void loadPayments(Context ctx) throws IOException, ParseException {
        Repository repo = ctx.repo;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Document doc = get(getPaymentsUrl(ctx));
        Elements th_row = doc.select("table").first().select("thead").first().select("tr").select("th");

        Fee fees[] = new Fee[th_row.size()];
        for (int i = 2; i < fees.length; i++) {
            // Col 0 = Namn
            // Col 1 = Typ
            // Col 2..N Avgift
            fees[i] = repo.getFee(th_row.get(i).attr("title"));
        }

        for (Element row : doc.select("table").first().select("tbody").first().select("tr")) {
            Elements cols = row.select("td");
            Player p = repo.getPlayerByType(cols.get(0).text(), Player.Type.parse(cols.get(1).text()));
            for (int i = 2; i < cols.size(); i++) {
                Payment pay = parsePayment(fees[i], cols.get(i));
                if (pay != null && p != null) {
                    pay.player = p;
                    repo.addPayment(pay);
                }
            }
        }
    }

    private String getDescriptionUrl(Activity activity) {
        return DESCRIPTION_URL + activity.id + "/";
    }

    private String getParticipantsUrl(Context ctx, Activity activity) {
        return PARTICIPANTS_URL + activity.id + "/";
    }

    private String getInvitationsUrl(Context ctx, Activity activity) {
        return BASE_URL +
            "/teams/" + ctx.prop.getProperty(TEAM_NO) +
            "/calendar/" + activity.id + "/view-invited";
    }

    private String getPlayerJsonUrl(Context ctx) {
      return PLAYER_JSON_URL + ctx.prop.getProperty(TEAM_NO) + "/members/json";
    }

    private String getPaymentsUrl(Context ctx) {
        return BASE_URL +
                "/teams/" + ctx.prop.getProperty(TEAM_NO) +
                "/fees/";
    }

    @Override
    public void logout() {
        authToken = null;
        super.logout();
    }

    public List<Specifier> listSections(Context ctx) throws IOException {
        return list(BASE_URL, "a[href*=change_section]");
    }

    @Override
    public List<Specifier> listTeams(Context ctx) throws Exception {
        return list(BASE_URL, "a[href*=change_team]");
    }

    @Override
    public List<Specifier> listPeriods(Context ctx) throws Exception {
        return list(CALENDAR_URL, "a[href*=T-2]");
    }

    @Override
    public void setSection(Context ctx, Specifier section) throws IOException {
        setSectionEx(section);
        setSpecifier(ctx, SECTION_NAME, SECTION_KEY, section);
    }

    @Override
    public Specifier getCurrentSection(Context ctx) {
        return getSpecifier(ctx, SECTION_NAME, SECTION_KEY);
    }

    @Override
    public void setTeam(Context ctx, Specifier team) throws IOException {
        setTeamEx(team);
        String teamno = team.key.substring(team.key.indexOf('=') + 1);
        ctx.prop.setProperty(TEAM_NO, teamno);
        setSpecifier(ctx, TEAM_NAME, TEAM_KEY, team);
    }

    @Override
    public Specifier getCurrentTeam(Context ctx) {
        return getSpecifier(ctx, TEAM_NAME, TEAM_KEY);
    }

    @Override
    public void setPeriod(Context ctx, Specifier period) throws IOException {
        setPeriodEx(period);
        setSpecifier(ctx, PERIOD_NAME, PERIOD_KEY, period);
    }

    @Override
    public Specifier getCurrentPeriod(Context ctx) {
        return getSpecifier(ctx, PERIOD_NAME, PERIOD_KEY);
    }

    private Specifier getSpecifier(Context ctx, String name, String key) {
        if (ctx.prop.containsKey(name) && ctx.prop.containsKey(key)) {
            return new Specifier(ctx.prop.getProperty(name),
                    ctx.prop.getProperty(key));
        }
        return null;
    }

    private void setSpecifier(Context ctx, String sectionName, String sectionKey, Specifier section) {
        if (section.isValid()) {
            ctx.prop.setProperty(sectionName, section.name);
            ctx.prop.setProperty(sectionKey, section.key);
        }
    }

    public void load(Context ctx) throws Exception {
        loadPlayers(ctx);
        loadPayments(ctx);
        loadActivities(ctx);
    }

    // Convert MyClub period name to start/end date
    public static Pair<Date, Date> periodName2Dates(String name) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, Integer.parseInt(name.substring(3)));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.clear(Calendar.MINUTE);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        if (name.startsWith("HT")) {
            cal.set(Calendar.MONTH, 7);
            Date startDate = cal.getTime();
            cal.set(Calendar.YEAR, cal.get(Calendar.YEAR) + 1);
            cal.set(Calendar.MONTH, 0);
            Date endDate = cal.getTime();
            return new Pair<>(startDate, endDate);
        } else {
            cal.set(Calendar.MONTH, 0);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            Date startDate = cal.getTime();
            cal.set(Calendar.MONTH, 7);
            Date endDate = cal.getTime();
            return new Pair<>(startDate, endDate);
        }
    }

    public List<Specifier> GetCurrent(Context ctx) {
        List<Specifier> ret = new ArrayList<>();
        Specifier section = getCurrentSection(ctx);
        if (section == null) {
            return ret;
        }
        ret.add(section);
        Specifier team = getCurrentTeam(ctx);
        if (team == null) {
            return ret;
        }
        ret.add(team);
        Specifier period = getCurrentPeriod(ctx);
        if (period == null)
            return ret;
        ret.add(period);
        return ret;
    }
}

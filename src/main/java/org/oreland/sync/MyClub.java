package org.oreland.sync;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.oreland.db.Repository;
import org.oreland.entity.Activity;
import org.oreland.sync.util.FormValues;
import org.oreland.sync.util.SyncHelper;
import org.oreland.ui.Dialog;
import org.oreland.ui.DialogBuilder;

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


/**
 * Created by jonas on 10/1/15.
 */
public class MyClub extends DefaultSynchronizer {

    public static final String CHARSET = "utf-8";
    public static final String START_URL = "https://www.myclub.se/";
    public static final String BASE_URL = "https://member.myclub.se";
    public static final String LOGIN_URL = "https://accounts.myclub.se/d/users/sign_in"; //.json";
    public static final String CALENDAR_URL = BASE_URL + "/activities/team/show/";
    public static final String PARTICIPANTS_URL = BASE_URL + "/activities/team/view_parts/";
    public static final String INVITATIONS_URL = BASE_URL + "/activities/team/view_invited/";
    public static final String DESCRIPTION_URL = BASE_URL + "/activities/team/edit/";

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

    public Status setup (Properties config, DialogBuilder builder) {
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
        Document doc;
        try {
            if (!config.containsKey("club")) {
                String club = select("Select club: ", builder, BASE_URL, "a[href*=change_club]");
                config.setProperty("club", club);
            }
            doc = get(BASE_URL+config.getProperty("club"));
        } catch (IOException e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        try {
            if (!config.containsKey("section")) {
                String club = select("Select section: ", builder, BASE_URL, "a[href*=change_section]");
                config.setProperty("section", club);
            }
            doc = get(BASE_URL+config.getProperty("section"));
        } catch (IOException e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        try {
            if (!config.containsKey("team")) {
                String club = select("Select team: ", builder, BASE_URL, "a[href*=change_team]");
                config.setProperty("team", club);
            }
            doc = get(BASE_URL+config.getProperty("team"));
        } catch (IOException e) {
            e.printStackTrace();
            return Status.ERROR;
        }

        return Status.OK;
    }

    private String select(String prompt, DialogBuilder builder, String baseUrl, String query) throws IOException {
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
        builder.setMultipleChoices(choices);
        Dialog.Result result = builder.build().show();
        return choiceValues.get(result.intResult - 1);
    }

    private Document get(String baseUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl).openConnection();
        conn.setInstanceFollowRedirects(false);
        addRequestHeaders(conn);
        conn = send(baseUrl, conn);
        int responseCode = conn.getResponseCode();
        String amsg = conn.getResponseMessage();
        Document doc = Jsoup.parse(conn.getInputStream(), "UTF-8", baseUrl);
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
                int responseCode = conn.getResponseCode();
                String amsg = conn.getResponseMessage();
                System.out.println("code: " + responseCode + ", msg: " + amsg);
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
                int responseCode = conn.getResponseCode();
                String amsg = conn.getResponseMessage();
                System.out.println("code: " + responseCode + ", msg: " + amsg);
                getFormValues(conn);
                authToken = formValues.get("authenticity_token");
            }
            conn.disconnect();

            System.out.println("authToken: " + authToken);
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
                int responseCode = conn.getResponseCode();
                String amsg = conn.getResponseMessage();
                System.out.println("code: " + responseCode + ", msg: " + amsg);
            }

            conn = (HttpURLConnection) new URL(BASE_URL).openConnection();
            conn.setInstanceFollowRedirects(false);
            addRequestHeaders(conn);
            {
                conn = send(START_URL, conn);
                int responseCode = conn.getResponseCode();
                String amsg = conn.getResponseMessage();
                System.out.println("code: " + responseCode + ", msg: " + amsg);
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

    HttpURLConnection send(String startUrl, HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
    	if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                String newUrl = conn.getHeaderField("Location");
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                addRequestHeaders(conn);
                return send(newUrl, conn);
            }
        }
        return conn;
    }

    public void loadActivities(Repository repo) throws IOException, ParseException {
        Calendar c = Calendar.getInstance();
        Date today = c.getTime();
        c.add(Calendar.DAY_OF_YEAR, 7);
        Date limit = c.getTime();

        Document doc = get(CALENDAR_URL);
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
            if (type.equals("Match")) {
                String desc = columns.get(0).text();
                String date = columns.get(1).text().substring(0, 8);
                String id = columns.get(7).select("a[href]").first().attr("href").replace("/activities/team/view_parts/", "").replace("/", "");
                activity = repo.add(new Activity(id, formatter.parse(date), desc, Activity.Type.GAME));
                System.out.println(date + " - " + desc + " " + id);
            } else if (type.matches("Tr.*ning")) {
                String desc = columns.get(0).text();
                String date = columns.get(1).text().substring(0, 8);
                String id = columns.get(7).select("a[href]").first().attr("href").replace("/activities/team/view_parts/", "").replace("/", "");
                activity = repo.add(new Activity(id, formatter.parse(date), desc, Activity.Type.TRAINING));
            }
            if (activity != null && activity.synced == false && activity.date.before(limit)) {
                if (activity.level == null) {
                    loadActivityLevel(repo, activity);
                }
                loadInvitations(repo, activity);
                loadParticipants(repo, activity);
                if (activity.date.after(today))
                    activity.synced = true;
            }
        }
    }

    private void loadActivityLevel(Repository repo, Activity activity) throws IOException {
        Document doc = get(getDescriptionUrl(activity));
    }

    private void loadParticipants(Repository repo, Activity activity) throws IOException {
        Document doc = get(getParticipantsUrl(activity));
    }

    public void loadInvitations(Repository repo, Activity activity) throws IOException {
        Document doc = get(getInvitationsUrl(activity));
        
    }

    private String getDescriptionUrl(Activity activity) {
        return DESCRIPTION_URL + activity.id + "/";
    }

    private String getParticipantsUrl(Activity activity) {
        return PARTICIPANTS_URL + activity.id + "/";
    }

    private String getInvitationsUrl(Activity activity) {
        return INVITATIONS_URL + activity.id + "/";
    }

    @Override
    public void logout() {
        authToken = null;
        super.logout();
    }
}

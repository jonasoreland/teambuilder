package org.oreland.sync;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.oreland.sync.util.FormValues;
import org.oreland.sync.util.StringWritable;
import org.oreland.sync.util.SyncHelper;
import org.oreland.ui.Dialog;
import org.oreland.ui.DialogBuilder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;


/**
 * Created by jonas on 10/1/15.
 */
public class MyClub extends DefaultSynchronizer {

    public static final String START_URL = "https://www.myclub.se/";
    public static final String BASE_URL = "https://member.myclub.se";
    public static final String LOGIN_URL = "https://accounts.myclub.se/d/users/sign_in"; //.json";

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
        conn.addRequestProperty("Accept-Encoding", "");
        conn.addRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.addRequestProperty("Connection", "keep-alive");
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
        System.out.println("code: " + responseCode + ", msg: " + amsg);
        String html = SyncHelper.readInputStream(conn.getInputStream());
        conn.disconnect();
        return Jsoup.parse(html);
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

    @Override
    public void logout() {
        authToken = null;
        super.logout();
    }
}

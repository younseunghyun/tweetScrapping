package edu.yonsei.scapper.twitter.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.LimitExceededException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class TwitterAPIScrapper {

    private Properties prop;
    private String keyword;
    private String max_id;
    private int fileNum = 0;

    public TwitterAPIScrapper(Properties prop) {
        this.prop = prop;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void scrapping()
        throws Exception
    {
        String encodedBearKey = getBearKey();
        System.out.println();
        System.out.println(String.format("bearer access_token : %s", encodedBearKey));

        JSONArray result;

        while (true) {
            try {
                result = scrapTweet(encodedBearKey);
            } catch (LimitExceededException le) {
                System.out.println(String.format("api request limit exceed. wait for 1 minute"));
                Thread.sleep(60 * 1000);
                continue;
            }
            if (!(result.size() > 0)) break;
            toFile(result);
            System.out.println(String.format("accumulated tweet is %s", fileNum * 100));

        }
        System.out.println(String.format("%s done", keyword));

    }

    private String getBearKey()
        throws IOException, ParseException
    {
        String oauthUrl = "https://api.twitter.com/oauth2/token";
        URL accessTokenUrl = new URL(oauthUrl);
        HttpURLConnection con = (HttpURLConnection) accessTokenUrl.openConnection();
        con.setRequestMethod("POST");
        String bearerToken = getConvertedConsumerKey() + ":" + getConvertedConsumersecret();

        System.out.println(String.format("ConsumerKey : %s, CosumerSecretKey : %s", prop.get("ConsumerKey"), prop.get("ConsumerSecret")));
        System.out.println(String.format("Encoded ConsumerKey : %s, Encoded CosumerSecretKey : %s", getConvertedConsumerKey(), getConvertedConsumersecret()));
        System.out.println(String.format("BearerToken : %s", bearerToken));

        con.setRequestProperty("Authorization", "Basic " + Base64.getUrlEncoder().encodeToString(bearerToken.getBytes()));
        con.setDoOutput(true);

        String urlParm = "grant_type=client_credentials";
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParm);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + oauthUrl);
        System.out.println("Post parameters : " + urlParm);
        System.out.println("Response Code : " + responseCode);
        BufferedReader serverResponse = new BufferedReader(new InputStreamReader(con.getInputStream()));

        JSONObject obj = new JSONObject();
        JSONParser jsonParser = new JSONParser();
        obj = (JSONObject) jsonParser.parse(serverResponse.readLine());

        return (String) obj.get("access_token");

    }

    private JSONArray scrapTweet(String encodedBearKey)
        throws Exception
    {
        Map<String, String> parmMap = new HashMap<>();
        parmMap.put("q", keyword);
        parmMap.put("result_type", "recent");
        parmMap.put("count", "100");
        parmMap.put("max_id", max_id != null ? max_id : "");

        String searchUrl = "https://api.twitter.com/1.1/search/tweets.json" + "?" + String.join("&", parmMaker(parmMap));
        URL accessTokenUrl = new URL(searchUrl);
        HttpURLConnection con = (HttpURLConnection) accessTokenUrl.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Bearer " + encodedBearKey);
        con.setRequestProperty("Host", "api.twitter.com");

        con.setDoOutput(true);

        int responseCode = con.getResponseCode();
        if (responseCode != 200) {
            System.out.println(String.format("error code : %s", responseCode));
            throw new LimitExceededException();
        }
        System.out.println("\nSending 'GET' request to URL : " + searchUrl);
        System.out.println("Response Code : " + responseCode);

        try (BufferedReader serverResponse = new BufferedReader(new InputStreamReader(con.getInputStream()));) {
            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject) parser.parse(serverResponse.readLine());
            JSONArray result = (JSONArray) obj.get("statuses");
            if (result.size() > 0) {
                max_id = getMaxId(result);
            }
            return (JSONArray) obj.get("statuses");
        } catch (Exception e) {
            e.printStackTrace();
            try (BufferedReader errorResponse = new BufferedReader(new InputStreamReader(con.getErrorStream()));) {
                String errorLine = errorResponse.readLine();
                System.out.println(errorLine);

            }
            throw new Exception("fail to get tweet");
        }

    }

    @SuppressWarnings("unchecked")
    private void toFile(JSONArray result) {

        File target = new File(String.format("tweets/%s", keyword, fileNum));
        if (!target.isDirectory()) target.mkdirs();

        File fi = new File(String.format("tweets/%s/%s.txt", keyword, fileNum));
        try (BufferedWriter brw = new BufferedWriter(new FileWriter(fi))) {
            result.forEach(json -> {
                try {
                    brw.write(((JSONObject) json).toJSONString());
                    brw.newLine();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        ++fileNum;
    }

    private List<String> parmMaker(Map<String, String> kvMap)
    {
        List<String> encodeParms = new ArrayList<>();
        kvMap.forEach((k, v) -> {
            try {
                String tmp = k + "=" + urlencodeKey(v);
                encodeParms.add(tmp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return encodeParms;
    }

    private String getMaxId(JSONArray jsonArray) {
        List<BigInteger> tweetIdList = new ArrayList<>();
        for (Object obj : jsonArray) {
            tweetIdList.add(new BigInteger(Long.toString((Long) ((JSONObject) obj).get("id"))));
        }
        tweetIdList.sort((c1, c2) -> (c1.compareTo(c2) > 0) ? 1 : -1);
        return tweetIdList.get(0).toString();

    }

    private String urlencodeKey(String key)
        throws UnsupportedEncodingException
    {
        return URLEncoder.encode(key, "utf8");
    }

    private String getConvertedConsumerKey()
        throws UnsupportedEncodingException
    {
        return urlencodeKey(prop.getProperty("ConsumerKey"));
    }

    private String getConvertedConsumersecret()
        throws UnsupportedEncodingException
    {
        return urlencodeKey(prop.getProperty("ConsumerSecret"));
    }
}

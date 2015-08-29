package edu.yonsei.scapper.twitter.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;


public class App
{
    public static void main(String[] args)
        throws Exception
    {
        Properties prop = new Properties();
        try (InputStream is = new FileInputStream(new File("./application.properties"))) {
            prop.load(new FileInputStream(new File("./application.properties")));
        } catch (FileNotFoundException fe) {
            System.out.println("properties file not exsist");
        }
        TwitterAPIScrapper twitterAPIScrapper = new TwitterAPIScrapper(prop);
        String keyword = args[0];
        twitterAPIScrapper.setKeyword(keyword);
        System.out.println(String.format("keyword : %s",keyword));
        twitterAPIScrapper.scrapping();
    }
}

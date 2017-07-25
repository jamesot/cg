package com.cadre.ocr;

import android.util.Log;

/**
 * Created by danielmerrill on 5/30/15.
 */
public class StringUtils {

    final static String TAG = "ocrlive.StringUtils";

    public static String clean(String s){
        String cleanString = s.toLowerCase();
        // replace all non alphabetic characters with spaces
        cleanString = cleanString.replaceAll("[^A-Za-z\\n ]", " ");
        return cleanString;
    }



    public static String centerWord(String s){
        int centerWordStart = 0;
        int centerWordEnd = s.length();

        int midpoint = s.length() / 2;
        // if the midpoint is a space scoot it back one index
        Log.i(TAG, s + " midpoint: " + midpoint);
        /*
        if (Character.toString(s.charAt(midpoint)).equals(" ")) {
            midpoint -= 1;
        }*/

        // Cut stiring in half
        String startToMid = s.substring(0, midpoint);
        // Find the last space in the first half of the phrase
        if (startToMid.lastIndexOf(" ", midpoint-1) > 0) {
            centerWordStart = startToMid.lastIndexOf(" ", midpoint) + 1;
        }

        // Find the first space in the last half of the phrase
        if (s.indexOf(" ", midpoint) > 0) {
            centerWordEnd = s.indexOf(" ", midpoint);
        }

        String word;
        if (centerWordEnd == s.length()) {
            word = s.substring(centerWordStart);
        } else {
            word = s.substring(centerWordStart, centerWordEnd);
        }

        Log.i(TAG, "Word = " + word);
        Log.i(TAG, "End string length =" + s.length() + " String start index=" + centerWordStart + " End index= " + centerWordEnd);

        return word;
    }

    /*

    Takes raw input which may or may not have multiple lines, parses them into individual lines
    and finds the center word in each

     */

    public static String parseLines(String s) {

        String centerWord = "";
        String cleanedString = clean(s);
        String lines[] = cleanedString.split("\\r?\\n");
        for (String string : lines){
            if (string.length() > 0) {
                centerWord = centerWord(string);
            }
        }
    // returns the center word in the last line
    return centerWord;
    }
}
